import Foundation
import Network

struct HTTPError: LocalizedError {
    let message: String
    init(_ message: String) { self.message = message }
    var errorDescription: String? { message }
}

struct Reply {
    var status: Int
    var headers: [String: String]  // names lowercased
    var url: URL                   // after redirects
}

/// One TCP (or TLS) connection, optionally pinned to an interface type. NWConnection is what lets
/// a socket be forced onto mobile data while Wi-Fi is the default route; URLSession can't.
final class Conn {
    private let c: NWConnection
    private let queue = DispatchQueue(label: "plexo.conn")

    init(url: URL, via: NWInterface.InterfaceType?) throws {
        guard let host = url.host, let scheme = url.scheme?.lowercased(), scheme == "http" || scheme == "https" else {
            throw HTTPError("That isn't a web link")
        }
        let port = UInt16(url.port ?? (scheme == "https" ? 443 : 80))
        let params: NWParameters = scheme == "https" ? .tls : .tcp
        if let via = via { params.requiredInterfaceType = via }
        c = NWConnection(host: NWEndpoint.Host(host), port: NWEndpoint.Port(rawValue: port)!, using: params)
    }

    func cancel() { c.cancel() }

    func ready() async throws {
        try await withCheckedThrowingContinuation { (k: CheckedContinuation<Void, Error>) in
            var finished = false
            c.stateUpdateHandler = { state in
                if finished { return }
                switch state {
                case .ready: finished = true; k.resume()
                case .failed(let e): finished = true; k.resume(throwing: e)
                // waiting: there is no route over this interface right now (mobile data off, no Wi-Fi). Fail fast.
                case .waiting(let e): finished = true; k.resume(throwing: e)
                case .cancelled: finished = true; k.resume(throwing: CancellationError())
                default: break
                }
            }
            c.start(queue: queue)
        }
    }

    func send(_ d: Data) async throws {
        try await withCheckedThrowingContinuation { (k: CheckedContinuation<Void, Error>) in
            c.send(content: d, completion: .contentProcessed { e in
                if let e = e { k.resume(throwing: e) } else { k.resume() }
            })
        }
    }

    /// Up to 64 KB; nil once the server has closed; possibly empty (the caller just asks again).
    func recv() async throws -> Data? {
        try await withCheckedThrowingContinuation { (k: CheckedContinuation<Data?, Error>) in
            c.receive(minimumIncompleteLength: 1, maximumLength: 65_536) { data, _, isComplete, error in
                if let error = error { k.resume(throwing: error) }
                else if let data = data, !data.isEmpty { k.resume(returning: data) }
                else if isComplete { k.resume(returning: nil) }
                else { k.resume(returning: Data()) }
            }
        }
    }
}

/// Reads lines and bodies off a Conn.
final class Reader {
    private let conn: Conn
    private var buf = Data()
    private var eof = false

    init(_ conn: Conn) { self.conn = conn }

    private func fill() async throws {
        if eof { return }
        if let d = try await conn.recv() { buf.append(d) } else { eof = true }
    }

    /// A line without its CRLF; nil if the connection ended first.
    func line() async throws -> String? {
        while true {
            if let r = buf.range(of: Data("\r\n".utf8)) {
                let s = String(decoding: buf[buf.startIndex..<r.lowerBound], as: UTF8.self)
                buf.removeSubrange(buf.startIndex..<r.upperBound)
                return s
            }
            if eof { return nil }
            try await fill()
        }
    }

    /// 1...n bytes; empty only at the end of the connection.
    func take(_ n: Int) async throws -> Data {
        while buf.isEmpty && !eof { try await fill() }
        let k = min(n, buf.count)
        let d = Data(buf.prefix(k))
        buf.removeFirst(k)
        return d
    }
}

enum HTTP {
    /// GET over HTTP/1.1, one connection per request, redirects followed by hand on the same interface.
    /// `onBody` gets the body in pieces, but only when the status is one of `bodyStatus`; any other
    /// reply is returned unread, for the caller to judge.
    static func get(
        _ start: URL, via: NWInterface.InterfaceType?, range: String? = nil, headersOnly: Bool = false,
        bodyStatus: Set<Int> = [200, 206], onBody: @escaping (Data) async throws -> Void = { _ in }
    ) async throws -> Reply {
        var url = start
        for _ in 0..<8 {
            let conn = try Conn(url: url, via: via)
            let reply: Reply = try await withTaskCancellationHandler(
                operation: {
                    defer { conn.cancel() }
                    return try await exchange(conn, url, range, headersOnly, bodyStatus, onBody)
                },
                onCancel: { conn.cancel() })
            if [301, 302, 303, 307, 308].contains(reply.status) {
                guard let loc = reply.headers["location"], let next = URL(string: loc, relativeTo: url)?.absoluteURL else {
                    throw HTTPError("The server sent a bad redirect")
                }
                url = next
                continue
            }
            return reply
        }
        throw HTTPError("Too many redirects")
    }

    private static func exchange(
        _ conn: Conn, _ url: URL, _ range: String?, _ headersOnly: Bool, _ bodyStatus: Set<Int>,
        _ onBody: (Data) async throws -> Void
    ) async throws -> Reply {
        try await conn.ready()

        let comps = URLComponents(url: url, resolvingAgainstBaseURL: false)
        var target = comps?.percentEncodedPath ?? "/"
        if target.isEmpty { target = "/" }
        if let q = comps?.percentEncodedQuery { target += "?" + q }
        var host = url.host ?? ""
        if host.contains(":") { host = "[\(host)]" }
        if let p = url.port { host += ":\(p)" }

        var req = "GET \(target) HTTP/1.1\r\nHost: \(host)\r\nUser-Agent: Plexo/1.0\r\nAccept: */*\r\n"
        req += "Accept-Encoding: identity\r\nConnection: close\r\n"  // a gzipped body has no usable byte ranges
        if let r = range { req += "Range: \(r)\r\n" }
        try await conn.send(Data((req + "\r\n").utf8))

        let rd = Reader(conn)
        guard let statusLine = try await rd.line() else { throw HTTPError("The server didn't answer") }
        let parts = statusLine.split(separator: " ", maxSplits: 2)
        guard parts.count >= 2, let status = Int(parts[1]) else { throw HTTPError("The server sent something that isn't HTTP") }
        var headers: [String: String] = [:]
        while let l = try await rd.line(), !l.isEmpty {
            if let i = l.firstIndex(of: ":") {
                headers[l[..<i].lowercased()] = l[l.index(after: i)...].trimmingCharacters(in: .whitespaces)
            }
        }
        let reply = Reply(status: status, headers: headers, url: url)
        if headersOnly || !bodyStatus.contains(status) { return reply }

        let closed = HTTPError("The connection closed early")
        if headers["transfer-encoding"]?.lowercased().contains("chunked") == true {
            while true {
                guard let sizeLine = try await rd.line() else { throw closed }
                let hex = sizeLine.split(separator: ";").first.map { $0.trimmingCharacters(in: .whitespaces) } ?? ""
                guard let size = Int(hex, radix: 16) else { throw HTTPError("The server sent broken data") }
                if size == 0 { break }
                var left = size
                while left > 0 {
                    let d = try await rd.take(min(left, 65_536))
                    if d.isEmpty { throw closed }
                    left -= d.count
                    try await onBody(d)
                }
                _ = try await rd.line()  // the CRLF after each chunk
            }
        } else if let len = headers["content-length"].flatMap({ Int64($0) }) {
            var left = len
            while left > 0 {
                let d = try await rd.take(Int(min(left, 65_536)))
                if d.isEmpty { throw closed }
                left -= Int64(d.count)
                try await onBody(d)
            }
        } else {  // no length: until the server closes
            while true {
                let d = try await rd.take(65_536)
                if d.isEmpty { break }
                try await onBody(d)
            }
        }
        return reply
    }
}

/// What a link turns out to be: where it really lives, how big it is, whether it can be split.
struct ProbeResult {
    var finalURL: URL
    var fileName: String
    var total: Int64   // -1: the server did not say
    var ranges: Bool

    var json: [String: Any] {
        ["finalUrl": finalURL.absoluteString, "fileName": fileName, "total": total, "ranges": ranges]
    }

    /// A one-byte range request: cheaper than the body, and the 206 shows ranges really work.
    static func run(_ link: String, via: NWInterface.InterfaceType?) async throws -> ProbeResult {
        guard let url = URL(string: link.trimmingCharacters(in: .whitespacesAndNewlines)) else { throw HTTPError("That isn't a web link") }
        let r = try await HTTP.get(url, via: via, range: "bytes=0-0", headersOnly: true)
        var total: Int64 = -1
        var ranges = false
        switch r.status {
        case 206:
            if let cr = r.headers["content-range"], let slash = cr.lastIndex(of: "/") {
                total = Int64(cr[cr.index(after: slash)...].trimmingCharacters(in: .whitespaces)) ?? -1  // "*": unknown
            }
            ranges = total >= 0
        case 200: total = Int64(r.headers["content-length"] ?? "") ?? -1
        case 416: total = 0; ranges = true  // a range into an empty file
        default: throw HTTPError(describe(r.status))
        }
        let name = fromDisposition(r.headers["content-disposition"]) ?? r.url.lastPathComponent
        return ProbeResult(finalURL: r.url, fileName: clean(name), total: total, ranges: ranges)
    }

    static func describe(_ code: Int) -> String {
        switch code {
        case 401, 403: return "The server refused the link (HTTP \(code)). It may need a sign-in."
        case 404, 410: return "The file isn't there any more (HTTP \(code))"
        default: return "The server answered HTTP \(code)"
        }
    }

    static func fromDisposition(_ cd: String?) -> String? {
        guard let cd = cd else { return nil }
        func first(_ pattern: String, _ group: Int) -> String? {
            guard let re = try? NSRegularExpression(pattern: pattern, options: .caseInsensitive),
                  let m = re.firstMatch(in: cd, range: NSRange(cd.startIndex..., in: cd)),
                  let r = Range(m.range(at: group), in: cd) else { return nil }
            return String(cd[r]).trimmingCharacters(in: .whitespaces)
        }
        if let star = first("filename\\*\\s*=\\s*[^']*'[^']*'([^;]+)", 1), let decoded = star.removingPercentEncoding { return decoded }
        return first("filename\\s*=\\s*\"?([^\";]+)\"?", 1)
    }

    /// A name that is safe as a file name.
    static func clean(_ name: String) -> String {
        let bad = CharacterSet(charactersIn: "\\/:*?\"<>|").union(.controlCharacters)
        var n = name.components(separatedBy: bad).joined(separator: "_").trimmingCharacters(in: .whitespaces)
        if n.count > 120 { n = String(n.suffix(120)) }
        return n.isEmpty || n == "." || n == ".." ? "download" : n
    }
}

import Foundation

/// Which chunks of a ranged download wait, are in flight or are done, and how much of each is
/// already on disk. The same job as ChunkMap.java: every stream, on every network, claims from here.
final class ChunkMap {
    static let size = 4 << 20

    let total: Int64
    let count: Int
    private var state: [UInt8]  // 0 waiting, 1 in flight, 2 done
    private var owner: [UInt8]  // index of the network that delivered each done chunk
    private var got: [Int]      // bytes of each chunk already written, from its start
    private var done = 0, busy = 0, cursor = 0
    private var written: Int64 = 0
    private let lock = NSLock()

    init(total: Int64) {
        self.total = total
        count = Int((total + Int64(ChunkMap.size) - 1) / Int64(ChunkMap.size))
        state = Array(repeating: 0, count: count)
        owner = Array(repeating: 0, count: count)
        got = Array(repeating: 0, count: count)
    }

    /// The next waiting chunk, now marked in flight; nil when none waits.
    func claim() -> Int? {
        lock.lock(); defer { lock.unlock() }
        while cursor < count && state[cursor] != 0 { cursor += 1 }
        if cursor == count { return nil }
        state[cursor] = 1
        busy += 1
        return cursor
    }

    func from(_ i: Int) -> Int64 {
        lock.lock(); defer { lock.unlock() }
        return Int64(i) * Int64(ChunkMap.size) + Int64(got[i])
    }

    func to(_ i: Int) -> Int64 { min(total, Int64(i + 1) * Int64(ChunkMap.size)) - 1 }

    func add(_ i: Int, _ n: Int) {
        lock.lock(); defer { lock.unlock() }
        got[i] += n
        written += Int64(n)
    }

    func finish(_ i: Int, net: Int) {
        lock.lock(); defer { lock.unlock() }
        state[i] = 2; owner[i] = UInt8(net); busy -= 1; done += 1
    }

    /// Gives a chunk back (failed, or its stream was stopped); what was written is kept.
    func release(_ i: Int) {
        lock.lock(); defer { lock.unlock() }
        state[i] = 0; busy -= 1
        if i < cursor { cursor = i }
    }

    var complete: Bool { lock.lock(); defer { lock.unlock() }; return done == count }
    var hasWaiting: Bool { lock.lock(); defer { lock.unlock() }; return done + busy < count }
    var bytes: Int64 { lock.lock(); defer { lock.unlock() }; return written }

    /// The file as at most `max` cells: "." waiting, "*" downloading, a digit once done by that network.
    func cells(max: Int) -> String {
        lock.lock(); defer { lock.unlock() }
        if count == 0 { return "" }
        let group = (count + max - 1) / max
        var out = ""
        var s = 0
        while s < count {
            let e = min(count, s + group)
            var by = [Int](repeating: 0, count: 10)
            var active = false, finished = 0
            for i in s..<e {
                if state[i] == 1 { active = true }
                else if state[i] == 2 { finished += 1; by[min(9, Int(owner[i]))] += 1 }
            }
            if finished == e - s {
                var top = 0
                for n in 1..<10 where by[n] > by[top] { top = n }
                out += String(top)
            } else {
                out += active ? "*" : "."
            }
            s = e
        }
        return out
    }
}

/// A speed cap shared by every stream on one network: each read reserves its slot in time.
final class Limiter {
    private let lock = NSLock()
    private var cap: Int64 = 0
    private var next: UInt64 = 0

    var bytesPerSec: Int64 {
        get { lock.lock(); defer { lock.unlock() }; return cap }
        set { lock.lock(); defer { lock.unlock() }; cap = max(0, newValue) }
    }

    /// Nanoseconds the caller must wait before using `n` bytes.
    func reserve(_ n: Int) -> UInt64 {
        lock.lock(); defer { lock.unlock() }
        guard cap > 0 else { return 0 }
        let now = DispatchTime.now().uptimeNanoseconds
        if next < now { next = now }
        let wait = next - now
        next += UInt64(n) * 1_000_000_000 / UInt64(cap)
        return wait
    }
}

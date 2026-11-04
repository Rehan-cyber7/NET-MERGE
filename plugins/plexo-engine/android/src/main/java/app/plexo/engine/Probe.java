package app.plexo.engine;

import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONException;
import org.json.JSONObject;

/** What a link turns out to be: where it really lives, how big it is, whether it can be split. */
final class Probe {
    String finalUrl;
    String fileName;
    long total = -1; // -1: the server did not say
    boolean ranges;

    private static final Pattern STAR = Pattern.compile("filename\\*\\s*=\\s*([^']*)'[^']*'([^;]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAIN = Pattern.compile("filename\\s*=\\s*\"?([^\";]+)\"?", Pattern.CASE_INSENSITIVE);

    /** A one-byte range request: cheaper than the body, and the 206 shows ranges really work. */
    static Probe run(Nets nets, String netId, String url) throws IOException {
        Nets.Resp r = nets.open(netId, url, "bytes=0-0");
        try {
            Probe p = new Probe();
            p.finalUrl = r.url;
            if (r.code == 206) {
                String cr = r.conn.getHeaderField("Content-Range"); // "bytes 0-0/12345"
                int slash = cr == null ? -1 : cr.lastIndexOf('/');
                try {
                    p.total = slash < 0 ? -1 : Long.parseLong(cr.substring(slash + 1).trim());
                } catch (NumberFormatException e) {
                    p.total = -1; // "*": size unknown
                }
                p.ranges = p.total >= 0;
            } else if (r.code == 200) {
                p.total = r.conn.getContentLengthLong();
            } else if (r.code == 416) {
                p.total = 0; // a range into an empty file
                p.ranges = true;
            } else {
                throw new IOException(describe(r.code));
            }
            String name = fromDisposition(r.conn.getHeaderField("Content-Disposition"));
            p.fileName = clean(name != null ? name : fromUrl(r.url));
            return p;
        } finally {
            r.conn.disconnect();
        }
    }

    JSONObject json() {
        try {
            return new JSONObject().put("finalUrl", finalUrl).put("fileName", fileName).put("total", total).put("ranges", ranges);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    static String describe(int code) {
        if (code == 401 || code == 403) return "The server refused the link (HTTP " + code + "). It may need a sign-in.";
        if (code == 404 || code == 410) return "The file isn't there any more (HTTP " + code + ")";
        return "The server answered HTTP " + code;
    }

    static String message(Exception e) {
        String m = e.getMessage();
        return m == null || m.isEmpty() ? e.getClass().getSimpleName() : m;
    }

    static String fromDisposition(String cd) {
        if (cd == null) return null;
        Matcher m = STAR.matcher(cd);
        if (m.find()) {
            try {
                String cs = m.group(1).isEmpty() ? "UTF-8" : m.group(1);
                return URLDecoder.decode(m.group(2).trim().replace("+", "%2B"), cs);
            } catch (Exception ignored) { }
        }
        m = PLAIN.matcher(cd);
        return m.find() ? m.group(1).trim() : null;
    }

    static String fromUrl(String url) {
        try {
            String path = new URL(url).getPath();
            return URLDecoder.decode(path.substring(path.lastIndexOf('/') + 1).replace("+", "%2B"), "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    /** A name that is safe as a file name on shared storage. */
    static String clean(String name) {
        String n = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (n.length() > 120) n = n.substring(n.length() - 120);
        return n.isEmpty() || n.equals(".") || n.equals("..") ? "download" : n;
    }
}

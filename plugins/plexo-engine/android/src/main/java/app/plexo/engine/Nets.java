package app.plexo.engine;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The connections a download can use at once. Android lets an app ask for each transport by name
 * and open sockets bound to the Network it gets, even while another one is the default: that is
 * what makes Wi-Fi + mobile data at the same time possible.
 */
final class Nets {
    static final String[] IDS = {"wifi", "cellular", "ethernet"};
    static final String[] NAMES = {"Wi-Fi", "Mobile data", "Ethernet"};
    private static final int[] TRANSPORT = {
        NetworkCapabilities.TRANSPORT_WIFI, NetworkCapabilities.TRANSPORT_CELLULAR, NetworkCapabilities.TRANSPORT_ETHERNET
    };

    static final class Resp {
        final HttpURLConnection conn;
        final String url; // after redirects
        final int code;

        Resp(HttpURLConnection conn, String url, int code) {
            this.conn = conn;
            this.url = url;
            this.code = code;
        }
    }

    private final ConnectivityManager cm;
    private final Runnable changed;
    private final Map<String, Network> up = new ConcurrentHashMap<>();
    private final ConnectivityManager.NetworkCallback[] callbacks = new ConnectivityManager.NetworkCallback[IDS.length];

    Nets(Context ctx, Runnable changed) {
        this.cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        this.changed = changed;
    }

    boolean online(String id) { return up.containsKey(id); }

    /** Asks Android to keep every transport up (this is what wakes mobile data while on Wi-Fi). Idempotent. */
    synchronized void request() {
        for (int i = 0; i < IDS.length; i++) {
            if (callbacks[i] != null) continue;
            final String id = IDS[i];
            NetworkRequest req = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(TRANSPORT[i])
                .build();
            ConnectivityManager.NetworkCallback cb = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network n) { up.put(id, n); changed.run(); }
                @Override public void onLost(Network n) { up.remove(id, n); changed.run(); }
            };
            try {
                cm.requestNetwork(req, cb);
                callbacks[i] = cb;
            } catch (RuntimeException e) {
                // no permission, or too many requests: this transport just stays offline
            }
        }
    }

    /** Lets the radios idle when nothing is downloading and the app is out of sight. */
    synchronized void release() {
        for (int i = 0; i < IDS.length; i++) {
            if (callbacks[i] == null) continue;
            try { cm.unregisterNetworkCallback(callbacks[i]); } catch (RuntimeException ignored) { }
            callbacks[i] = null;
            up.remove(IDS[i]);
        }
    }

    /**
     * A request that has already got its status line. Redirects are followed by hand so that every
     * hop stays on the same network; `netId` null uses the system default.
     */
    Resp open(String netId, String url, String range) throws IOException {
        Network net = netId == null ? null : up.get(netId);
        if (netId != null && net == null) throw new IOException("Not connected");
        for (int hop = 0; hop < 8; hop++) {
            URL u = new URL(url);
            HttpURLConnection c = (HttpURLConnection) (net != null ? net.openConnection(u) : u.openConnection());
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(15_000);
            c.setReadTimeout(20_000);
            c.setRequestProperty("User-Agent", "Plexo/1.0");
            c.setRequestProperty("Accept-Encoding", "identity"); // a gzipped body has no usable byte ranges
            if (range != null) c.setRequestProperty("Range", range);
            int code;
            try {
                code = c.getResponseCode();
            } catch (IOException e) {
                c.disconnect();
                throw e;
            }
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new IOException("The server sent a bad redirect");
                url = new URL(u, loc).toString();
                continue;
            }
            return new Resp(c, url, code);
        }
        throw new IOException("Too many redirects");
    }
}

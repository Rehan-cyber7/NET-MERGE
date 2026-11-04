package app.plexo.engine;

/**
 * Tracks byte ranges for a download. Chunks are initially spread round-robin over the active
 * networks so the first network whose worker thread happens to run cannot claim the entire file.
 * A different active network can take over chunks assigned to an unavailable/failed network, and
 * can help with remaining chunks once every active network has claimed its own waiting work.
 */
final class ChunkMap {
    /** Largest chunk size; smaller files use smaller chunks so both transports can participate. */
    static final int SIZE = 4 << 20;
    private static final int MEDIUM_SIZE = 1 << 20;
    private static final int SMALL_SIZE = 256 << 10;

    private static final byte PENDING = 0, BUSY = 1, DONE = 2;

    final long total;
    final int count;
    final int chunkSize;
    private final byte[] state;
    private final byte[] owner; // network index that delivered each completed chunk
    private final byte[] preferred; // network index initially assigned each chunk; -1 means unassigned
    private final int[] got; // bytes of each chunk already written, from its start
    private int done, busy;
    private long bytes;

    ChunkMap(long total, int[] activeNetworkIndices) {
        this.total = Math.max(0, total);
        if (this.total <= 8L * SIZE) chunkSize = SMALL_SIZE;
        else if (this.total <= 64L * SIZE) chunkSize = MEDIUM_SIZE;
        else chunkSize = SIZE;

        this.count = (int) ((this.total + chunkSize - 1) / chunkSize);
        state = new byte[count];
        owner = new byte[count];
        preferred = new byte[count];
        got = new int[count];

        if (activeNetworkIndices == null || activeNetworkIndices.length == 0) {
            java.util.Arrays.fill(preferred, (byte) -1);
        } else {
            for (int i = 0; i < count; i++) {
                preferred[i] = (byte) activeNetworkIndices[i % activeNetworkIndices.length];
            }
        }
    }

    /** Compatibility constructor for the core unit test: all chunks belong to network zero. */
    ChunkMap(long total) {
        this(total, new int[] {0});
    }

    /**
     * Claim a chunk for one network. Each network takes its own assigned chunks first. A network
     * may take another network's pending chunk only when that owner is no longer usable, or when
     * all pending chunks assigned to currently usable networks have already been claimed.
     */
    synchronized int claim(int netIndex, boolean[] usable) {
        if (netIndex < 0 || netIndex >= usable.length || !usable[netIndex]) return -1;
        for (int i = 0; i < count; i++) {
            if (state[i] == PENDING && preferred[i] == (byte) netIndex) return markBusy(i);
        }

        // Do not strand chunks assigned to a network that has gone offline, been disabled, or failed.
        for (int i = 0; i < count; i++) {
            int assigned = preferred[i];
            if (state[i] == PENDING && (assigned < 0 || assigned >= usable.length || !usable[assigned])) {
                return markBusy(i);
            }
        }

        // All available owners have claimed their own pending chunks. Let an active network help
        // with the remaining work instead of leaving its workers idle.
        boolean pendingForUsableOwner = false;
        for (int i = 0; i < count; i++) {
            int assigned = preferred[i];
            if (state[i] == PENDING && assigned >= 0 && assigned < usable.length && usable[assigned]) {
                pendingForUsableOwner = true;
                break;
            }
        }
        if (!pendingForUsableOwner) {
            for (int i = 0; i < count; i++) if (state[i] == PENDING) return markBusy(i);
        }
        return -1;
    }

    private int markBusy(int i) {
        state[i] = BUSY;
        busy++;
        return i;
    }

    synchronized long from(int i) { return (long) i * chunkSize + got[i]; }

    long to(int i) { return Math.min(total, (long) (i + 1) * chunkSize) - 1; }

    synchronized void add(int i, int len) { got[i] += len; bytes += len; }

    synchronized void finish(int i, int net) { state[i] = DONE; owner[i] = (byte) net; busy--; done++; }

    /** Gives a chunk back (failed, or its stream was stopped); what was written is kept. */
    synchronized void release(int i) {
        state[i] = PENDING;
        busy--;
    }

    synchronized boolean complete() { return done == count; }

    synchronized boolean hasWaiting() { return done + busy < count; }

    synchronized long bytes() { return bytes; }

    /** The file as at most `max` cells: '.', '*' downloading, or a digit once done by that network. */
    synchronized String cells(int max) {
        if (count == 0) return "";
        int group = (count + max - 1) / max;
        StringBuilder sb = new StringBuilder();
        for (int s = 0; s < count; s += group) {
            int e = Math.min(count, s + group);
            int[] by = new int[10];
            boolean active = false;
            int finished = 0;
            for (int i = s; i < e; i++) {
                if (state[i] == BUSY) active = true;
                else if (state[i] == DONE) { finished++; by[Math.min(9, owner[i])]++; }
            }
            if (finished == e - s) {
                int top = 0;
                for (int n = 1; n < 10; n++) if (by[n] > by[top]) top = n;
                sb.append((char) ('0' + top));
            } else {
                sb.append(active ? '*' : '.');
            }
        }
        return sb.toString();
    }
}

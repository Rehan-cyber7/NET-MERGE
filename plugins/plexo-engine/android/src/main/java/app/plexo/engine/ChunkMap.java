package app.plexo.engine;

/**
 * Which chunks of a ranged download wait, are in flight or are done, and how much of each is
 * already on disk. Streams on every network claim from here, so a faster network takes more chunks.
 */
final class ChunkMap {
    static final int SIZE = 4 << 20;
    private static final byte PENDING = 0, BUSY = 1, DONE = 2;

    final long total;
    final int count;
    private final byte[] state;
    private final byte[] owner; // index of the network that delivered each done chunk
    private final int[] got; // bytes of each chunk already written, from its start
    private int done, busy, cursor;
    private long bytes;

    ChunkMap(long total) {
        this.total = total;
        this.count = (int) ((total + SIZE - 1) / SIZE);
        state = new byte[count];
        owner = new byte[count];
        got = new int[count];
    }

    /** The next waiting chunk, now marked in flight; -1 when none waits. */
    synchronized int claim() {
        while (cursor < count && state[cursor] != PENDING) cursor++;
        if (cursor == count) return -1;
        state[cursor] = BUSY;
        busy++;
        return cursor;
    }

    synchronized long from(int i) { return (long) i * SIZE + got[i]; }

    long to(int i) { return Math.min(total, (long) (i + 1) * SIZE) - 1; }

    synchronized void add(int i, int len) { got[i] += len; bytes += len; }

    synchronized void finish(int i, int net) { state[i] = DONE; owner[i] = (byte) net; busy--; done++; }

    /** Gives a chunk back (failed, or its stream was stopped); what was written is kept. */
    synchronized void release(int i) {
        state[i] = PENDING;
        busy--;
        if (i < cursor) cursor = i;
    }

    synchronized boolean complete() { return done == count; }

    synchronized boolean hasWaiting() { return done + busy < count; }

    synchronized long bytes() { return bytes; }

    /** The file as at most `max` cells: '.' waiting, '*' downloading, a digit once done by that network. */
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

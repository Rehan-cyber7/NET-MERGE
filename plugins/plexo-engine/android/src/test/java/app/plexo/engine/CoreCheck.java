package app.plexo.engine;

/** Assert-based check of the scheduling core; run with `java -ea` (see README). */
public class CoreCheck {
    public static void main(String[] a) throws Exception {
        long total = 80L * ChunkMap.SIZE + 123; // uses 4 MiB chunks; last chunk is short
        ChunkMap m = new ChunkMap(total, new int[] {0, 1});
        assert m.count == 81;
        assert m.to(80) == total - 1 && m.to(0) == ChunkMap.SIZE - 1;

        boolean[] both = {true, true, false};
        int wifi = m.claim(0, both);
        int cell = m.claim(1, both);
        assert wifi == 0 && cell == 1; // round-robin ownership prevents one transport taking all
        m.add(wifi, 1000);
        m.release(wifi); // failed: a later claim resumes where it stopped
        assert m.claim(0, both) == 0 && m.from(0) == 1000;
        m.finish(0, 0);
        m.finish(1, 1);
        assert m.bytes() == 1000 && !m.complete() && m.hasWaiting();

        int n;
        while ((n = m.claim(0, both)) >= 0) m.finish(n, 0);
        while ((n = m.claim(1, both)) >= 0) m.finish(n, 1);
        assert m.complete() && !m.hasWaiting() && m.claim(0, both) == -1;
        assert m.cells(100).length() == 81 && m.cells(4).length() == 4;
        assert m.cells(100).startsWith("01"): m.cells(100);

        ChunkMap small = new ChunkMap(3L * 1024 * 1024, new int[] {0, 1});
        assert small.chunkSize == 256 * 1024;
        assert small.count == 12; // a 3 MiB file can now be shared by two networks
        assert small.claim(0, both) == 0;
        assert small.claim(1, both) == 1;

        ChunkMap empty = new ChunkMap(0, new int[] {0}); // an empty file is already complete
        assert empty.complete() && empty.claim(0, both) == -1 && empty.cells(10).isEmpty();

        ChunkMap offline = new ChunkMap(3L * ChunkMap.SIZE, new int[] {0, 1});
        boolean[] onlyCell = {false, true, false};
        assert offline.claim(0, onlyCell) == -1; // An offline network cannot claim work.
        assert offline.claim(1, onlyCell) == 1; // Cellular first claims its own chunk.
        for (int i = 0; i < 23; i++) assert offline.claim(1, onlyCell) >= 0;
        assert offline.claim(1, onlyCell) == 0; // Wi-Fi-owned work can move to cellular if Wi-Fi is offline.

        Limiter l = new Limiter();
        assert l.reserve(1 << 20) == 0; // no cap, no wait
        l.bytesPerSec = 1_000_000;
        l.reserve(500_000);
        long wait = l.reserve(500_000); // the second half-megabyte waits for the first
        assert wait > 400_000_000L && wait < 600_000_000L : wait;
        System.out.println("core ok");
    }
}

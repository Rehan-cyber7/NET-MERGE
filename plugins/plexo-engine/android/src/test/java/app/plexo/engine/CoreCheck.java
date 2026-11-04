package app.plexo.engine;

/** Assert-based check of the scheduling core; run with `java -ea` (see README). */
public class CoreCheck {
    public static void main(String[] a) throws Exception {
        long total = 10L * ChunkMap.SIZE + 123; // 11 chunks, the last one short
        ChunkMap m = new ChunkMap(total);
        assert m.count == 11;
        assert m.to(10) == total - 1 && m.to(0) == ChunkMap.SIZE - 1;

        int c0 = m.claim(), c1 = m.claim();
        assert c0 == 0 && c1 == 1;
        m.add(c0, 1000);
        m.release(c0); // failed: the next claim resumes where it stopped
        assert m.claim() == 0 && m.from(0) == 1000;
        m.finish(0, 0);
        m.finish(1, 1);
        assert m.bytes() == 1000 && !m.complete() && m.hasWaiting();

        int n;
        while ((n = m.claim()) >= 0) m.finish(n, n % 2);
        assert m.complete() && !m.hasWaiting() && m.claim() == -1;
        assert m.cells(20).length() == 11 && m.cells(4).length() == 4;
        assert m.cells(20).startsWith("01"): m.cells(20);

        ChunkMap empty = new ChunkMap(0); // an empty file is already complete
        assert empty.complete() && empty.claim() == -1 && empty.cells(10).isEmpty();

        ChunkMap p = new ChunkMap(3L * ChunkMap.SIZE);
        p.claim();
        assert p.cells(10).equals("*..") && p.hasWaiting();

        Limiter l = new Limiter();
        assert l.reserve(1 << 20) == 0; // no cap, no wait
        l.bytesPerSec = 1_000_000;
        l.reserve(500_000);
        long wait = l.reserve(500_000); // the second half-megabyte waits for the first
        assert wait > 400_000_000L && wait < 600_000_000L : wait;
        System.out.println("core ok");
    }
}

package app.plusnet.engine;

/** A speed cap shared by every stream on one network: each read reserves its slot in time. */
final class Limiter {
    volatile long bytesPerSec; // 0: no cap
    private long next;

    /** Nanoseconds the caller must wait before using `n` bytes. */
    synchronized long reserve(int n) {
        long cap = bytesPerSec;
        if (cap <= 0) return 0;
        long now = System.nanoTime();
        if (next < now) next = now;
        long wait = next - now;
        next += n * 1_000_000_000L / cap;
        return wait;
    }
}

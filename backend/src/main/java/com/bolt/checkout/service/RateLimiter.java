package com.bolt.checkout.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window rate limiter keyed by client address.
 *
 * <p>Intentionally in-process: the assignment runs a single backend instance, and a shared
 * store (Redis, or a database table) would only be needed once the backend is scaled
 * horizontally - at which point this class is the single place that has to change.
 *
 * <p>Safe for concurrent use: the backing map is concurrent and each window is guarded by
 * its own monitor, so many request threads can share one instance.
 */
public class RateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;

    /** Above this many tracked keys, stale windows are swept on the next request. */
    private static final int SWEEP_THRESHOLD = 10_000;

    private final int limit;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter(int limit) {
        this.limit = limit;
    }

    /**
     * Records a request against {@code key}.
     *
     * @return the outcome, including how long the caller should wait when limited
     */
    public Decision tryAcquire(String key) {
        if (limit <= 0) {
            // A non-positive limit disables the limiter entirely.
            return Decision.permit();
        }

        long now = System.currentTimeMillis();
        Window window = windows.computeIfAbsent(key, k -> new Window(now));

        int used;
        long retryAfterSeconds;
        synchronized (window) {
            if (now - window.startedAt >= WINDOW_MILLIS) {
                window.startedAt = now;
                window.used = 0;
            }
            window.used++;
            used = window.used;
            retryAfterSeconds = Math.max(1, (window.startedAt + WINDOW_MILLIS - now) / 1000);
        }

        if (windows.size() > SWEEP_THRESHOLD) {
            sweep(now);
        }

        return used > limit ? Decision.reject(retryAfterSeconds) : Decision.permit();
    }

    private void sweep(long now) {
        windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt >= WINDOW_MILLIS);
    }

    /** Forgets all counters. Used by tests. */
    public void reset() {
        windows.clear();
    }

    public int getLimit() {
        return limit;
    }

    private static final class Window {
        private long startedAt;
        private int used;

        private Window(long startedAt) {
            this.startedAt = startedAt;
        }
    }

    /** Result of a rate-limit check. */
    public record Decision(boolean allowed, long retryAfterSeconds) {

        static Decision permit() {
            return new Decision(true, 0);
        }

        static Decision reject(long retryAfterSeconds) {
            return new Decision(false, retryAfterSeconds);
        }
    }
}

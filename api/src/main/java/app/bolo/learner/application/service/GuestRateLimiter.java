package app.bolo.learner.application.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/** A sliding-window counter per key, in memory (one API instance). Old keys are dropped so it can't grow forever. */
public class GuestRateLimiter {

    private static final int PRUNE_ABOVE_KEYS = 10_000;

    private final int limit;
    private final Duration window;
    private final Map<String, Deque<Instant>> hits = new HashMap<>();

    public GuestRateLimiter(int limit, Duration window) {
        this.limit = limit;
        this.window = window;
    }

    public synchronized boolean tryAcquire(String key, Instant now) {
        Instant cutoff = now.minus(window);
        if (hits.size() > PRUNE_ABOVE_KEYS) {
            hits.values().removeIf(q -> q.isEmpty() || !q.peekLast().isAfter(cutoff));
        }
        Deque<Instant> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!q.isEmpty() && !q.peekFirst().isAfter(cutoff)) q.pollFirst();
        if (q.size() >= limit) return false;
        q.addLast(now);
        return true;
    }
}

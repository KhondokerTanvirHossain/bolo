package app.bolo.learner.application.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Sliding-window caps on new guests, in memory (one API instance): a generous per-key cap (many real learners share
 * one carrier or campus IP) and a global cap that bounds disk growth whatever the addresses. Old keys are dropped.
 */
public class GuestRateLimiter {

    private static final int PRUNE_ABOVE_KEYS = 10_000;

    private final int perKey;
    private final int global;
    private final Duration window;
    private final Map<String, Deque<Instant>> hits = new HashMap<>();
    private final Deque<Instant> all = new ArrayDeque<>();

    public GuestRateLimiter(int perKey, int global, Duration window) {
        this.perKey = perKey;
        this.global = global;
        this.window = window;
    }

    public synchronized boolean tryAcquire(String key, Instant now) {
        Instant cutoff = now.minus(window);
        if (hits.size() > PRUNE_ABOVE_KEYS) {
            hits.values().removeIf(q -> q.isEmpty() || !q.peekLast().isAfter(cutoff));
        }
        drop(all, cutoff);
        Deque<Instant> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        drop(q, cutoff);
        if (q.size() >= perKey || all.size() >= global) return false;
        q.addLast(now);
        all.addLast(now);
        return true;
    }

    private static void drop(Deque<Instant> q, Instant cutoff) {
        while (!q.isEmpty() && !q.peekFirst().isAfter(cutoff)) q.pollFirst();
    }
}

package io.gauss.augur.version;

import io.gauss.core.annotation.ModelVersion;
import io.gauss.core.annotation.ModelVersions;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Weighted random traffic router for multi-version {@code @MLEndpoint}s (HU-019).
 *
 * <p>Given a list of {@link VersionWeight} entries the router selects a version
 * proportionally on each call.  Weights are runtime-mutable without restart.
 *
 * <p>Per-version call counts are maintained so that
 * {@code StatisticalTestService} can collect samples without additional
 * instrumentation.
 *
 * <p>Usage:
 * <pre>{@code
 * VersionRouter router = VersionRouter.fromAnnotations(ChurnEndpoint.class);
 * String version = router.route();   // "v1" 80% of the time, "v2" 20%
 * }</pre>
 */
public final class VersionRouter {

    private volatile List<VersionWeight> weights;
    private final ReadWriteLock          lock    = new ReentrantReadWriteLock();
    private final Map<String, AtomicLong> counts = new ConcurrentHashMap<>();
    private final Random                 random;

    // -------------------------------------------------------------------------

    public VersionRouter(List<VersionWeight> weights) {
        this(weights, new Random());
    }

    /** Test constructor — accepts a seeded {@link Random} for deterministic routing. */
    public VersionRouter(List<VersionWeight> weights, Random random) {
        if (weights.isEmpty()) throw new IllegalArgumentException(
                "VersionRouter requires at least one version");
        this.weights = List.copyOf(weights);
        this.random  = random;
        weights.forEach(vw -> counts.put(vw.version(), new AtomicLong(0)));
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Builds a router by reading all {@link ModelVersion} annotations present
     * on {@code endpointClass}.
     *
     * @throws IllegalArgumentException if the class has no {@code @ModelVersion}
     */
    public static VersionRouter fromAnnotations(Class<?> endpointClass) {
        ModelVersions container = endpointClass.getAnnotation(ModelVersions.class);
        ModelVersion  single    = endpointClass.getAnnotation(ModelVersion.class);

        List<VersionWeight> ws;
        if (container != null) {
            ws = Arrays.stream(container.value())
                    .map(mv -> new VersionWeight(mv.value(), mv.weight()))
                    .toList();
        } else if (single != null) {
            ws = List.of(new VersionWeight(single.value(), single.weight()));
        } else {
            throw new IllegalArgumentException(
                    endpointClass.getSimpleName() + " has no @ModelVersion annotations");
        }
        return new VersionRouter(ws);
    }

    // -------------------------------------------------------------------------
    // Routing
    // -------------------------------------------------------------------------

    /**
     * Selects and returns a version identifier according to the current weights.
     * Increments the call counter for the chosen version.
     */
    public String route() {
        lock.readLock().lock();
        try {
            int total = weights.stream().mapToInt(VersionWeight::weight).sum();
            int pick  = random.nextInt(total);
            int cumulative = 0;
            for (VersionWeight vw : weights) {
                cumulative += vw.weight();
                if (pick < cumulative) {
                    counts.computeIfAbsent(vw.version(), k -> new AtomicLong()).incrementAndGet();
                    return vw.version();
                }
            }
            // Fallback (should never reach here)
            String last = weights.get(weights.size() - 1).version();
            counts.computeIfAbsent(last, k -> new AtomicLong()).incrementAndGet();
            return last;
        } finally {
            lock.readLock().unlock();
        }
    }

    // -------------------------------------------------------------------------
    // Weight management
    // -------------------------------------------------------------------------

    /**
     * Replaces the weight configuration at runtime without restarting the
     * application.  Existing call counts are preserved.
     *
     * @param newWeights the new weight list (must not be empty)
     */
    public void updateWeights(List<VersionWeight> newWeights) {
        if (newWeights.isEmpty()) throw new IllegalArgumentException(
                "newWeights must not be empty");
        lock.writeLock().lock();
        try {
            this.weights = List.copyOf(newWeights);
            newWeights.forEach(vw -> counts.putIfAbsent(vw.version(), new AtomicLong(0)));
        } finally {
            lock.writeLock().unlock();
        }
    }

    // -------------------------------------------------------------------------
    // Metrics
    // -------------------------------------------------------------------------

    /** Returns the total number of times {@code version} was selected. */
    public long callCount(String version) {
        AtomicLong c = counts.get(version);
        return c == null ? 0L : c.get();
    }

    /** Returns a snapshot of current weights. */
    public List<VersionWeight> currentWeights() {
        return weights;
    }

    /** Returns the number of configured versions. */
    public int versionCount() {
        return weights.size();
    }
}

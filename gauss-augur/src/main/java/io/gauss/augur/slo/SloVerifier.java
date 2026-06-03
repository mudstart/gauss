package io.gauss.augur.slo;

import io.gauss.core.annotation.LatencySLO;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verifies that measured latency statistics satisfy the targets declared in a
 * {@link LatencySLO} annotation (Augur module, HU-042).
 *
 * <p>Usage:
 * <pre>{@code
 * @MLEndpoint
 * @LatencySLO(p99 = "50ms", p95 = "20ms", p50 = "5ms")
 * class ChurnEndpoint { ... }
 *
 * SloVerifier verifier = new SloVerifier();
 * LatencyStats stats = measureEndpoint(ChurnEndpoint.class, 1000);
 * SloVerificationResult result = verifier.verify(ChurnEndpoint.class, stats);
 * result.violations().forEach(v -> log.error(v));
 * }</pre>
 */
public final class SloVerifier {

    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)(ms|s|m)");

    // -------------------------------------------------------------------------

    /**
     * Reads the {@link LatencySLO} annotation from {@code endpointClass} and
     * verifies all configured targets against {@code stats}.
     *
     * @param endpointClass the class bearing {@link LatencySLO}
     * @param stats         measured latency statistics
     * @return a verification result (pass or fail with violation details)
     * @throws IllegalArgumentException if the class has no {@link LatencySLO}
     */
    public SloVerificationResult verify(Class<?> endpointClass, LatencyStats stats) {
        LatencySLO slo = endpointClass.getAnnotation(LatencySLO.class);
        if (slo == null) throw new IllegalArgumentException(
                endpointClass.getSimpleName() + " has no @LatencySLO annotation");
        return verify(endpointClass.getSimpleName(), slo, stats);
    }

    /**
     * Verifies the targets directly from a {@link LatencySLO} annotation
     * instance.
     *
     * @param endpointName human-readable label for the endpoint
     * @param slo          the SLO annotation values
     * @param stats        measured latency statistics
     * @return verification result
     */
    public SloVerificationResult verify(String endpointName, LatencySLO slo, LatencyStats stats) {
        List<String> violations = new ArrayList<>();

        checkTarget(violations, "p50", slo.p50(), stats.p50(), endpointName);
        checkTarget(violations, "p95", slo.p95(), stats.p95(), endpointName);
        checkTarget(violations, "p99", slo.p99(), stats.p99(), endpointName);

        return new SloVerificationResult(endpointName, violations.isEmpty(), violations,
                stats.p50(), stats.p95(), stats.p99(), stats.sampleCount());
    }

    // -------------------------------------------------------------------------

    private static void checkTarget(List<String> violations,
                                     String percentileLabel,
                                     String targetStr,
                                     long actualMs,
                                     String endpointName) {
        if (targetStr == null || targetStr.isBlank()) return;
        long targetMs = parseMs(targetStr);
        if (actualMs > targetMs) {
            violations.add(String.format(
                    "[%s] %s SLO violated: target=%dms actual=%dms",
                    endpointName, percentileLabel, targetMs, actualMs));
        }
    }

    /**
     * Parses a duration string ({@code "50ms"}, {@code "2s"}, {@code "1m"})
     * into milliseconds.
     */
    public static long parseMs(String s) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException("Empty duration");
        Matcher m = DURATION_PATTERN.matcher(s.trim());
        if (!m.matches()) throw new IllegalArgumentException("Invalid duration: " + s);
        long value = Long.parseLong(m.group(1));
        return switch (m.group(2)) {
            case "ms" -> value;
            case "s"  -> value * 1_000;
            case "m"  -> value * 60_000;
            default   -> throw new IllegalArgumentException("Unknown unit in: " + s);
        };
    }
}

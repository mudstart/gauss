package io.gauss.lex.admin;

import java.time.Instant;
import java.util.Map;

/**
 * Aggregated system state snapshot for the admin dashboard (HU-036).
 *
 * <p>Exposed via {@code GET /dsml/admin/overview} and rendered as status
 * cards in the admin UI.
 *
 * @param modelsTotal        total registered models
 * @param modelsInProduction models currently in PRODUCTION stage
 * @param pipelinesScheduled number of pipelines with a cron schedule
 * @param experimentsTotal   total experiment runs recorded
 * @param featuresTotal      total registered features
 * @param namespacesTotal    total distinct namespaces
 * @param componentHealth    health status per component name
 * @param generatedAt        snapshot timestamp
 */
public record SystemOverview(
        int                 modelsTotal,
        int                 modelsInProduction,
        int                 pipelinesScheduled,
        int                 experimentsTotal,
        int                 featuresTotal,
        int                 namespacesTotal,
        Map<String, String> componentHealth,
        Instant             generatedAt
) {

    public SystemOverview {
        componentHealth = componentHealth == null ? Map.of() : Map.copyOf(componentHealth);
    }

    /** Returns {@code true} if all component health entries are "UP". */
    public boolean isHealthy() {
        return componentHealth.values().stream().allMatch("UP"::equals);
    }
}

package io.gauss.vigil.health;

import io.gauss.vigil.registry.ModelRegistration;
import io.gauss.vigil.registry.ModelRegistry;
import io.gauss.vigil.registry.Stage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link HealthIndicator} that reports the state of the Model Registry (HU-035).
 *
 * <p>Reports {@link HealthStatus#UP} when at least one model is registered.
 * Reports {@link HealthStatus#DEGRADED} when the registry is empty.
 * Any exception from the registry returns {@link HealthStatus#UNKNOWN}.
 */
public final class ModelRegistryHealthIndicator implements HealthIndicator {

    @Override
    public String name() {
        return "model-registry";
    }

    @Override
    public ComponentHealth check() {
        try {
            List<ModelRegistration> all = ModelRegistry.findAll();
            Map<String, Object> details = new LinkedHashMap<>();

            long stagingCount    = all.stream().filter(r -> r.currentStage() == Stage.STAGING).count();
            long productionCount = all.stream().filter(r -> r.currentStage() == Stage.PRODUCTION).count();
            long archivedCount   = all.stream().filter(r -> r.currentStage() == Stage.ARCHIVED).count();

            details.put("total",      all.size());
            details.put("staging",    stagingCount);
            details.put("production", productionCount);
            details.put("archived",   archivedCount);

            if (all.isEmpty()) {
                return ComponentHealth.degraded(name(),
                        "No models registered in the Model Registry");
            }
            return ComponentHealth.up(name(),
                    productionCount + " production model(s) registered", details);
        } catch (Exception e) {
            return ComponentHealth.unknown(name(), e);
        }
    }
}

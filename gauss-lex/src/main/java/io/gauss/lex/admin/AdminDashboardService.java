package io.gauss.lex.admin;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Aggregates system state from all Gauss modules and serves it to the admin
 * dashboard at {@code /dsml/admin} (HU-036).
 *
 * <p>Each module reports its state via simple counter/status registration
 * methods.  The service combines them into a {@link SystemOverview} on demand.
 *
 * <p>Usage:
 * <pre>{@code
 * AdminDashboardService admin = new AdminDashboardService();
 * admin.setModelsTotal(12);
 * admin.setModelsInProduction(3);
 * admin.setComponentHealth("model-registry", "UP");
 * admin.setComponentHealth("feature-store",  "UP");
 *
 * SystemOverview overview = admin.overview();
 * }</pre>
 */
public final class AdminDashboardService {

    private volatile int modelsTotal;
    private volatile int modelsInProduction;
    private volatile int pipelinesScheduled;
    private volatile int experimentsTotal;
    private volatile int featuresTotal;
    private volatile int namespacesTotal;
    private final Map<String, String> componentHealth = new ConcurrentHashMap<>();

    private final Supplier<Instant> clock;

    public AdminDashboardService() { this(Instant::now); }

    public AdminDashboardService(Supplier<Instant> clock) { this.clock = clock; }

    // -------------------------------------------------------------------------
    // State registration
    // -------------------------------------------------------------------------

    public void setModelsTotal(int n)        { this.modelsTotal = n; }
    public void setModelsInProduction(int n) { this.modelsInProduction = n; }
    public void setPipelinesScheduled(int n) { this.pipelinesScheduled = n; }
    public void setExperimentsTotal(int n)   { this.experimentsTotal = n; }
    public void setFeaturesTotal(int n)      { this.featuresTotal = n; }
    public void setNamespacesTotal(int n)    { this.namespacesTotal = n; }

    /**
     * Updates the health status for a named component.
     * Common values: {@code "UP"}, {@code "DOWN"}, {@code "DEGRADED"}.
     */
    public void setComponentHealth(String component, String status) {
        componentHealth.put(component, status);
    }

    // -------------------------------------------------------------------------
    // Dashboard query
    // -------------------------------------------------------------------------

    /**
     * Returns a point-in-time {@link SystemOverview} snapshot.
     * Called by the REST layer on each dashboard refresh.
     */
    public SystemOverview overview() {
        return new SystemOverview(
                modelsTotal, modelsInProduction,
                pipelinesScheduled, experimentsTotal,
                featuresTotal, namespacesTotal,
                Map.copyOf(componentHealth),
                clock.get());
    }
}

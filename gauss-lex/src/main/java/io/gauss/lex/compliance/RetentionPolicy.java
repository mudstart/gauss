package io.gauss.lex.compliance;

/**
 * Configures how long a category of data is retained before it qualifies for
 * automatic deletion (Lex module, HU-051).
 *
 * @param dataType name of the data category (e.g., {@code "predictions"},
 *                 {@code "features"}, {@code "experiments"}, {@code "audit_logs"})
 * @param ttlDays  number of calendar days after which data may be deleted;
 *                 {@code -1} means retain indefinitely (e.g., legal-hold audit logs)
 */
public record RetentionPolicy(String dataType, int ttlDays) {

    // ── Built-in defaults (configurable via dsml.retention.*) ─────────────────

    public static final RetentionPolicy PREDICTIONS  = new RetentionPolicy("predictions",   90);
    public static final RetentionPolicy FEATURES     = new RetentionPolicy("features",      365);
    public static final RetentionPolicy EXPERIMENTS  = new RetentionPolicy("experiments",   730);
    public static final RetentionPolicy AUDIT_LOGS   = new RetentionPolicy("audit_logs",    -1); // indefinite

    /** Returns {@code true} if data older than {@code ageDays} should be deleted. */
    public boolean isExpired(int ageDays) {
        return ttlDays >= 0 && ageDays >= ttlDays;
    }
}

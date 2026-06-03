package io.gauss.lex.audit;

/**
 * Enumeration of sensitive actions recorded in the audit log (HU-048).
 *
 * <p>Every action maps to a severity level used when exporting events in
 * CEF (Common Event Format) for ingestion by SIEM platforms such as Splunk
 * or Elastic.
 */
public enum AuditAction {

    // ── Model Registry ──────────────────────────────────────────────────────
    MODEL_REGISTERED   (3, "Model registered in the registry"),
    MODEL_PROMOTED     (5, "Model promoted to a new stage"),
    MODEL_ARCHIVED     (3, "Model archived"),
    MODEL_ROLLED_BACK  (7, "Automatic model rollback triggered"),

    // ── Pipeline ─────────────────────────────────────────────────────────────
    PIPELINE_EXECUTED  (3, "Data pipeline executed"),
    PIPELINE_SCHEDULED (2, "Pipeline schedule registered"),

    // ── Feature Store ─────────────────────────────────────────────────────────
    FEATURE_ACCESSED   (2, "Feature value accessed for entity"),
    FEATURE_COMPUTED   (2, "Feature value computed (cache miss)"),

    // ── Governance / Security ─────────────────────────────────────────────────
    PERMISSION_CHANGED (8, "User permission or role changed"),
    DATA_DELETED       (7, "Personal data deleted (GDPR right-to-erasure)"),
    LLM_INPUT_BLOCKED  (6, "LLM input rejected by guardrail"),

    // ── Generic ───────────────────────────────────────────────────────────────
    CONFIG_CHANGED     (5, "System configuration changed");

    private final int    cefSeverity;
    private final String description;

    AuditAction(int cefSeverity, String description) {
        this.cefSeverity = cefSeverity;
        this.description = description;
    }

    /** CEF severity level (0–10). */
    public int cefSeverity() { return cefSeverity; }

    /** Human-readable description of this action. */
    public String description() { return description; }
}

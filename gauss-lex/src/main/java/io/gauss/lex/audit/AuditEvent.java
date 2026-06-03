package io.gauss.lex.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable record of a single auditable action (HU-048).
 *
 * <p>Contains all information required by ISO 27001 and the EU AI Act:
 * who performed the action, what was affected, when, and from where.
 * Events are produced by {@link AuditEventBuilder} and appended to an
 * {@link AuditLog}.
 */
public record AuditEvent(
        String              id,
        String              actor,
        AuditAction         action,
        String              resource,
        String              namespace,
        String              ipAddress,
        Instant             timestamp,
        Map<String, String> details
) {

    /** Compact canonical constructor — defensive copy of details map. */
    public AuditEvent {
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    public static AuditEventBuilder builder(AuditAction action) {
        return new AuditEventBuilder(action);
    }

    // -------------------------------------------------------------------------
    // Export formats
    // -------------------------------------------------------------------------

    /**
     * Serializes this event as a CEF (Common Event Format) string suitable
     * for ingestion by Splunk, Elastic, or any CEF-compatible SIEM.
     *
     * <p>Format: {@code CEF:0|Gauss|Lex|1.0|<action>|<description>|<severity>|<ext>}
     */
    public String toCef() {
        String ext = String.format(
                "id=%s act=%s suser=%s src=%s cs1=%s cs1Label=namespace rt=%d msg=%s",
                id, action.name(), actor, ipAddress, namespace,
                timestamp.toEpochMilli(),
                detailsAsString());
        return String.format("CEF:0|Gauss|Lex|1.0|%s|%s|%d|%s",
                action.name(), action.description(), action.cefSeverity(), ext);
    }

    /** Returns the details map as a space-separated {@code key=value} string. */
    private String detailsAsString() {
        if (details.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        details.forEach((k, v) -> sb.append(k).append('=').append(v).append(' '));
        return sb.toString().trim();
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static final class AuditEventBuilder {

        private final AuditAction         action;
        private       String              id        = UUID.randomUUID().toString();
        private       String              actor     = "system";
        private       String              resource  = "";
        private       String              namespace = "default";
        private       String              ipAddress = "0.0.0.0";
        private       Instant             timestamp = Instant.now();
        private       Map<String, String> details   = Map.of();

        private AuditEventBuilder(AuditAction action) {
            this.action = action;
        }

        public AuditEventBuilder id(String v)                   { id        = v; return this; }
        public AuditEventBuilder actor(String v)                { actor     = v; return this; }
        public AuditEventBuilder resource(String v)             { resource  = v; return this; }
        public AuditEventBuilder namespace(String v)            { namespace = v; return this; }
        public AuditEventBuilder ipAddress(String v)            { ipAddress = v; return this; }
        public AuditEventBuilder timestamp(Instant v)           { timestamp = v; return this; }
        public AuditEventBuilder details(Map<String, String> v) { details   = v; return this; }

        public AuditEvent build() {
            return new AuditEvent(id, actor, action, resource,
                                  namespace, ipAddress, timestamp, details);
        }
    }
}

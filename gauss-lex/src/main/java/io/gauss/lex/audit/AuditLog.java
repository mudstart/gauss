package io.gauss.lex.audit;

import java.time.Instant;
import java.util.List;

/**
 * Append-only audit log SPI (HU-048).
 *
 * <p>All methods that return data are read-only; there is intentionally
 * no {@code delete} or {@code update} method — audit records are immutable
 * once written, satisfying the ISO 27001 non-repudiation requirement.
 *
 * <p>Implementations must be thread-safe.
 */
public interface AuditLog {

    /**
     * Appends an event.  Once appended an event can never be removed or
     * modified through this interface.
     *
     * @param event the event to record (must not be {@code null})
     */
    void append(AuditEvent event);

    /** Returns all recorded events in insertion order. */
    List<AuditEvent> findAll();

    /** Returns events where {@code actor} equals {@code actorName}. */
    List<AuditEvent> findByActor(String actorName);

    /** Returns events matching the given action type. */
    List<AuditEvent> findByAction(AuditAction action);

    /** Returns events whose {@code resource} field equals {@code resource}. */
    List<AuditEvent> findByResource(String resource);

    /** Returns events whose {@code namespace} field equals {@code namespace}. */
    List<AuditEvent> findByNamespace(String namespace);

    /**
     * Returns events whose timestamp is in the half-open interval
     * {@code [from, to)}.
     */
    List<AuditEvent> findBetween(Instant from, Instant to);

    /** Total number of recorded events. */
    long count();
}

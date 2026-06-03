package io.gauss.lex.audit;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe, in-memory implementation of {@link AuditLog} (HU-048).
 *
 * <p>Suitable for testing and single-node deployments.  For production use,
 * wire a persistent implementation backed by a write-ahead log or
 * an immutable database table.
 *
 * <p>This implementation is intentionally append-only — there is no eviction,
 * compaction, or deletion path exposed through the public API.
 */
public final class InMemoryAuditLog implements AuditLog {

    private final CopyOnWriteArrayList<AuditEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void append(AuditEvent event) {
        events.add(event);
    }

    @Override
    public List<AuditEvent> findAll() {
        return List.copyOf(events);
    }

    @Override
    public List<AuditEvent> findByActor(String actorName) {
        return events.stream()
                .filter(e -> e.actor().equals(actorName))
                .toList();
    }

    @Override
    public List<AuditEvent> findByAction(AuditAction action) {
        return events.stream()
                .filter(e -> e.action() == action)
                .toList();
    }

    @Override
    public List<AuditEvent> findByResource(String resource) {
        return events.stream()
                .filter(e -> e.resource().equals(resource))
                .toList();
    }

    @Override
    public List<AuditEvent> findByNamespace(String namespace) {
        return events.stream()
                .filter(e -> e.namespace().equals(namespace))
                .toList();
    }

    @Override
    public List<AuditEvent> findBetween(Instant from, Instant to) {
        return events.stream()
                .filter(e -> !e.timestamp().isBefore(from)
                          && e.timestamp().isBefore(to))
                .toList();
    }

    @Override
    public long count() {
        return events.size();
    }
}

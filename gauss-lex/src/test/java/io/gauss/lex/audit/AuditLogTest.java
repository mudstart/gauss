package io.gauss.lex.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link InMemoryAuditLog} and {@link AuditEvent}.
 * Covers HU-048 acceptance criteria.
 */
class AuditLogTest {

    private InMemoryAuditLog log;

    @BeforeEach
    void setUp() {
        log = new InMemoryAuditLog();
    }

    // -------------------------------------------------------------------------
    // AuditEvent builder
    // -------------------------------------------------------------------------

    @Test
    void builder_setsAction() {
        AuditEvent e = AuditEvent.builder(AuditAction.MODEL_PROMOTED).build();
        assertThat(e.action()).isEqualTo(AuditAction.MODEL_PROMOTED);
    }

    @Test
    void builder_defaultActor_isSystem() {
        assertThat(AuditEvent.builder(AuditAction.PIPELINE_EXECUTED).build().actor())
                .isEqualTo("system");
    }

    @Test
    void builder_defaultNamespace_isDefault() {
        assertThat(AuditEvent.builder(AuditAction.FEATURE_ACCESSED).build().namespace())
                .isEqualTo("default");
    }

    @Test
    void builder_customFields() {
        AuditEvent e = AuditEvent.builder(AuditAction.MODEL_PROMOTED)
                .actor("alice")
                .resource("model:churn-v2")
                .namespace("team-a")
                .ipAddress("10.0.0.1")
                .details(Map.of("stage", "PRODUCTION"))
                .build();
        assertThat(e.actor()).isEqualTo("alice");
        assertThat(e.resource()).isEqualTo("model:churn-v2");
        assertThat(e.namespace()).isEqualTo("team-a");
        assertThat(e.ipAddress()).isEqualTo("10.0.0.1");
        assertThat(e.details()).containsEntry("stage", "PRODUCTION");
    }

    @Test
    void builder_generatesUniqueIds() {
        String id1 = AuditEvent.builder(AuditAction.MODEL_PROMOTED).build().id();
        String id2 = AuditEvent.builder(AuditAction.MODEL_PROMOTED).build().id();
        assertThat(id1).isNotEqualTo(id2);
    }

    // -------------------------------------------------------------------------
    // CEF export
    // -------------------------------------------------------------------------

    @Test
    void toCef_startsWithCefPrefix() {
        AuditEvent e = AuditEvent.builder(AuditAction.MODEL_PROMOTED)
                .actor("alice").resource("model:churn").build();
        assertThat(e.toCef()).startsWith("CEF:0|Gauss|Lex|1.0|");
    }

    @Test
    void toCef_containsAction() {
        AuditEvent e = AuditEvent.builder(AuditAction.DATA_DELETED).build();
        assertThat(e.toCef()).contains("DATA_DELETED");
    }

    @Test
    void toCef_containsActor() {
        AuditEvent e = AuditEvent.builder(AuditAction.MODEL_PROMOTED)
                .actor("bob").build();
        assertThat(e.toCef()).contains("suser=bob");
    }

    @Test
    void toCef_containsSeverity() {
        // MODEL_PROMOTED has severity 5
        AuditEvent e = AuditEvent.builder(AuditAction.MODEL_PROMOTED).build();
        assertThat(e.toCef()).contains("|5|");
    }

    // -------------------------------------------------------------------------
    // AuditLog — append and findAll
    // -------------------------------------------------------------------------

    @Test
    void append_incrementsCount() {
        log.append(event(AuditAction.MODEL_PROMOTED, "alice", "model:v1", "ns-a"));
        assertThat(log.count()).isEqualTo(1);
    }

    @Test
    void findAll_returnsAllInOrder() {
        log.append(event(AuditAction.MODEL_PROMOTED,  "alice", "model:v1", "ns"));
        log.append(event(AuditAction.PIPELINE_EXECUTED,"bob",  "pipeline:etl", "ns"));
        assertThat(log.findAll()).hasSize(2);
        assertThat(log.findAll().get(0).actor()).isEqualTo("alice");
        assertThat(log.findAll().get(1).actor()).isEqualTo("bob");
    }

    @Test
    void findAll_returnsDefensiveCopy() {
        log.append(event(AuditAction.MODEL_PROMOTED, "alice", "r", "ns"));
        List<AuditEvent> copy = log.findAll();
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> copy.add(event(AuditAction.MODEL_ARCHIVED, "x", "r", "ns")));
    }

    // -------------------------------------------------------------------------
    // AuditLog — queries
    // -------------------------------------------------------------------------

    @Test
    void findByActor_returnsMatchingEvents() {
        log.append(event(AuditAction.MODEL_PROMOTED, "alice", "m1", "ns"));
        log.append(event(AuditAction.MODEL_PROMOTED, "bob",   "m2", "ns"));
        assertThat(log.findByActor("alice")).hasSize(1);
        assertThat(log.findByActor("alice").get(0).resource()).isEqualTo("m1");
    }

    @Test
    void findByAction_filtersCorrectly() {
        log.append(event(AuditAction.MODEL_PROMOTED,  "alice", "m1", "ns"));
        log.append(event(AuditAction.PIPELINE_EXECUTED,"alice","p1", "ns"));
        log.append(event(AuditAction.MODEL_PROMOTED,  "bob",  "m2", "ns"));
        assertThat(log.findByAction(AuditAction.MODEL_PROMOTED)).hasSize(2);
        assertThat(log.findByAction(AuditAction.PIPELINE_EXECUTED)).hasSize(1);
    }

    @Test
    void findByResource_exactMatch() {
        log.append(event(AuditAction.MODEL_PROMOTED, "alice", "model:churn-v1", "ns"));
        log.append(event(AuditAction.MODEL_PROMOTED, "alice", "model:churn-v2", "ns"));
        assertThat(log.findByResource("model:churn-v1")).hasSize(1);
    }

    @Test
    void findByNamespace_isolatesTeams() {
        log.append(event(AuditAction.MODEL_PROMOTED, "alice", "m1", "team-a"));
        log.append(event(AuditAction.MODEL_PROMOTED, "bob",   "m2", "team-b"));
        assertThat(log.findByNamespace("team-a")).hasSize(1);
        assertThat(log.findByNamespace("team-b")).hasSize(1);
    }

    @Test
    void findBetween_returnsEventsInRange() {
        Instant t0 = Instant.now();
        Instant t1 = t0.plusSeconds(10);
        Instant t2 = t0.plusSeconds(20);
        Instant t3 = t0.plusSeconds(30);

        log.append(AuditEvent.builder(AuditAction.MODEL_PROMOTED)
                .timestamp(t0).build());
        log.append(AuditEvent.builder(AuditAction.MODEL_PROMOTED)
                .timestamp(t1).build());
        log.append(AuditEvent.builder(AuditAction.MODEL_PROMOTED)
                .timestamp(t3).build());

        // [t0, t2) should contain t0 and t1, not t3
        assertThat(log.findBetween(t0, t2)).hasSize(2);
    }

    @Test
    void count_zero_initially() {
        assertThat(log.count()).isZero();
    }

    // -------------------------------------------------------------------------
    // Immutability guarantee (no delete path in API)
    // -------------------------------------------------------------------------

    @Test
    void auditLog_hasNoDeleteMethod() throws NoSuchMethodException {
        // Verify there is no delete/remove/clear method on the interface
        var methods = AuditLog.class.getMethods();
        for (var m : methods) {
            assertThat(m.getName())
                    .as("AuditLog must not expose a delete/remove/clear method")
                    .doesNotContainIgnoringCase("delete")
                    .doesNotContainIgnoringCase("remove")
                    .doesNotContainIgnoringCase("clear");
        }
    }

    // -------------------------------------------------------------------------
    // AuditAction metadata
    // -------------------------------------------------------------------------

    @Test
    void auditAction_dataDeleted_highSeverity() {
        assertThat(AuditAction.DATA_DELETED.cefSeverity()).isGreaterThanOrEqualTo(7);
    }

    @Test
    void auditAction_permissionChanged_highSeverity() {
        assertThat(AuditAction.PERMISSION_CHANGED.cefSeverity()).isGreaterThanOrEqualTo(7);
    }

    @Test
    void auditAction_featureAccessed_lowSeverity() {
        assertThat(AuditAction.FEATURE_ACCESSED.cefSeverity()).isLessThanOrEqualTo(3);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private static AuditEvent event(AuditAction action, String actor,
                                     String resource, String namespace) {
        return AuditEvent.builder(action)
                .actor(actor).resource(resource).namespace(namespace).build();
    }
}

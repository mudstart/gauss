package io.gauss.lex.compliance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ComplianceService}, {@link RetentionPolicy} and
 * {@link DeletionCertificate}.
 * Covers HU-051 acceptance criteria.
 */
class ComplianceServiceTest {

    private ComplianceService svc;

    @BeforeEach
    void setUp() {
        svc = new ComplianceService();
        svc.setRetentionPolicy(RetentionPolicy.PREDICTIONS);
        svc.setRetentionPolicy(RetentionPolicy.FEATURES);
        svc.setRetentionPolicy(RetentionPolicy.EXPERIMENTS);
        svc.setRetentionPolicy(RetentionPolicy.AUDIT_LOGS);
    }

    // -------------------------------------------------------------------------
    // RetentionPolicy
    // -------------------------------------------------------------------------

    @Test
    void retentionPolicy_predictions_90days() {
        assertThat(RetentionPolicy.PREDICTIONS.ttlDays()).isEqualTo(90);
    }

    @Test
    void retentionPolicy_auditLogs_indefinite() {
        assertThat(RetentionPolicy.AUDIT_LOGS.ttlDays()).isEqualTo(-1);
    }

    @Test
    void retentionPolicy_isExpired_belowTtl_false() {
        assertThat(RetentionPolicy.PREDICTIONS.isExpired(30)).isFalse();
    }

    @Test
    void retentionPolicy_isExpired_atTtl_true() {
        assertThat(RetentionPolicy.PREDICTIONS.isExpired(90)).isTrue();
    }

    @Test
    void retentionPolicy_isExpired_indefinite_neverExpires() {
        assertThat(RetentionPolicy.AUDIT_LOGS.isExpired(9999)).isFalse();
    }

    // -------------------------------------------------------------------------
    // Policy registration
    // -------------------------------------------------------------------------

    @Test
    void setRetentionPolicy_replacesExisting() {
        svc.setRetentionPolicy(new RetentionPolicy("predictions", 30));
        assertThat(svc.getPolicy("predictions").ttlDays()).isEqualTo(30);
    }

    @Test
    void getPolicy_unknownType_returnsDefault365() {
        assertThat(svc.getPolicy("unknown_type").ttlDays()).isEqualTo(365);
    }

    @Test
    void allPolicies_containsConfiguredTypes() {
        assertThat(svc.allPolicies()).containsKey("predictions");
        assertThat(svc.allPolicies()).containsKey("features");
    }

    // -------------------------------------------------------------------------
    // Subject data registration
    // -------------------------------------------------------------------------

    @Test
    void registerSubjectData_incrementsSubjectCount() {
        svc.registerSubjectData("user-1", "predictions", "pred-obj");
        assertThat(svc.subjectCount()).isEqualTo(1);
    }

    @Test
    void findBySubject_returnsAllRecords() {
        svc.registerSubjectData("user-1", "predictions", "p1");
        svc.registerSubjectData("user-1", "features",    "f1");
        assertThat(svc.findBySubject("user-1")).hasSize(2);
    }

    @Test
    void findBySubject_unknownSubject_returnsEmpty() {
        assertThat(svc.findBySubject("ghost")).isEmpty();
    }

    // -------------------------------------------------------------------------
    // deleteSubject — core erasure
    // -------------------------------------------------------------------------

    @Test
    void deleteSubject_removesAllRecords() {
        svc.registerSubjectData("user-42", "predictions", "p1");
        svc.registerSubjectData("user-42", "features",    "f1");
        svc.deleteSubject("user-42");
        assertThat(svc.findBySubject("user-42")).isEmpty();
        assertThat(svc.subjectCount()).isZero();
    }

    @Test
    void deleteSubject_returnsCertificate() {
        svc.registerSubjectData("user-7", "predictions", "p1");
        DeletionCertificate cert = svc.deleteSubject("user-7");
        assertThat(cert).isNotNull();
        assertThat(cert.subjectId()).isEqualTo("user-7");
    }

    @Test
    void deleteSubject_certificate_containsDeletedTypes() {
        svc.registerSubjectData("user-7", "predictions", "p1");
        svc.registerSubjectData("user-7", "features",    "f1");
        DeletionCertificate cert = svc.deleteSubject("user-7");
        assertThat(cert.deletedDataTypes()).containsExactlyInAnyOrder("predictions", "features");
    }

    @Test
    void deleteSubject_certificate_totalRecordsCorrect() {
        svc.registerSubjectData("user-7", "predictions", "p1");
        svc.registerSubjectData("user-7", "predictions", "p2");
        svc.registerSubjectData("user-7", "features",    "f1");
        DeletionCertificate cert = svc.deleteSubject("user-7");
        assertThat(cert.totalRecords()).isEqualTo(3);
    }

    @Test
    void deleteSubject_auditLogsNotDeletedFromCertificate() {
        svc.registerSubjectData("user-7", "predictions", "p1");
        svc.registerSubjectData("user-7", "audit_logs",  "a1");
        DeletionCertificate cert = svc.deleteSubject("user-7");
        assertThat(cert.deletedDataTypes()).doesNotContain("audit_logs");
        assertThat(cert.totalRecords()).isEqualTo(1);  // only "predictions" counted
    }

    @Test
    void deleteSubject_unknownSubject_returnsCertificateWithZeroRecords() {
        DeletionCertificate cert = svc.deleteSubject("nobody");
        assertThat(cert.totalRecords()).isZero();
        assertThat(cert.deletedDataTypes()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // DeletionCertificate
    // -------------------------------------------------------------------------

    @Test
    void certificate_hasUniqueId() {
        svc.registerSubjectData("u1", "predictions", "p1");
        svc.registerSubjectData("u2", "predictions", "p2");
        DeletionCertificate c1 = svc.deleteSubject("u1");
        DeletionCertificate c2 = svc.deleteSubject("u2");
        assertThat(c1.certificateId()).isNotEqualTo(c2.certificateId());
    }

    @Test
    void certificate_deletedAt_isSet() {
        svc.registerSubjectData("u3", "predictions", "p1");
        DeletionCertificate cert = svc.deleteSubject("u3");
        assertThat(cert.deletedAt()).isNotNull();
    }

    @Test
    void certificate_toText_containsSubjectId() {
        svc.registerSubjectData("user-999", "predictions", "p");
        DeletionCertificate cert = svc.deleteSubject("user-999");
        assertThat(cert.toText()).contains("user-999");
    }

    @Test
    void certificate_toText_mentionsGdpr() {
        svc.registerSubjectData("user-999", "predictions", "p");
        DeletionCertificate cert = svc.deleteSubject("user-999");
        assertThat(cert.toText()).containsIgnoringCase("GDPR");
    }

    // -------------------------------------------------------------------------
    // Certificate audit trail
    // -------------------------------------------------------------------------

    @Test
    void certificates_storedAfterDeletion() {
        svc.registerSubjectData("u1", "predictions", "p");
        svc.deleteSubject("u1");
        assertThat(svc.certificates()).hasSize(1);
    }

    @Test
    void certificates_accumulateAcrossMultipleDeletions() {
        svc.registerSubjectData("u1", "predictions", "p1");
        svc.registerSubjectData("u2", "predictions", "p2");
        svc.deleteSubject("u1");
        svc.deleteSubject("u2");
        assertThat(svc.certificates()).hasSize(2);
    }
}

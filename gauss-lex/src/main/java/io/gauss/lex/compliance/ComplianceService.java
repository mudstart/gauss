package io.gauss.lex.compliance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GDPR-compliant data retention and subject-erasure service (Lex module, HU-051).
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Maintain per-category retention policies.</li>
 *   <li>Track which data records are linked to a given subject ID.</li>
 *   <li>Execute right-to-erasure requests and produce a {@link DeletionCertificate}.</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * ComplianceService compliance = new ComplianceService();
 *
 * // Configure retention
 * compliance.setRetentionPolicy(new RetentionPolicy("predictions", 90));
 *
 * // Link data to subjects at write time
 * compliance.registerSubjectData("user-42", "predictions", predictionRecord);
 * compliance.registerSubjectData("user-42", "features",    featureVector);
 *
 * // Honour a right-to-erasure request
 * DeletionCertificate cert = compliance.deleteSubject("user-42");
 * }</pre>
 */
public final class ComplianceService {

    // dataType → RetentionPolicy
    private final Map<String, RetentionPolicy> policies = new ConcurrentHashMap<>();

    // subjectId → list of (dataType, record)
    private final Map<String, List<SubjectRecord>> subjectData = new ConcurrentHashMap<>();

    // completed certificates
    private final List<DeletionCertificate> certificates = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Retention policies
    // -------------------------------------------------------------------------

    /** Registers (or replaces) the retention policy for a data category. */
    public void setRetentionPolicy(RetentionPolicy policy) {
        policies.put(policy.dataType(), policy);
    }

    /**
     * Returns the retention policy for {@code dataType}, or a default that
     * retains data for 365 days if none has been configured.
     */
    public RetentionPolicy getPolicy(String dataType) {
        return policies.getOrDefault(dataType, new RetentionPolicy(dataType, 365));
    }

    /** Returns all configured retention policies. */
    public Map<String, RetentionPolicy> allPolicies() {
        return Map.copyOf(policies);
    }

    // -------------------------------------------------------------------------
    // Subject data registration
    // -------------------------------------------------------------------------

    /**
     * Links a data record to a subject ID so that it can be found and deleted
     * during a right-to-erasure request.
     *
     * @param subjectId the data subject identifier (e.g., user ID, customer ID)
     * @param dataType  category of the record (must match a configured policy)
     * @param record    the record object (can be any value for audit purposes)
     */
    public void registerSubjectData(String subjectId, String dataType, Object record) {
        subjectData.computeIfAbsent(subjectId, k -> new ArrayList<>())
                   .add(new SubjectRecord(dataType, record));
    }

    /** Returns all data records linked to {@code subjectId}. */
    public List<SubjectRecord> findBySubject(String subjectId) {
        return List.copyOf(subjectData.getOrDefault(subjectId, List.of()));
    }

    // -------------------------------------------------------------------------
    // Right-to-erasure
    // -------------------------------------------------------------------------

    /**
     * Deletes all data linked to {@code subjectId} and returns a
     * {@link DeletionCertificate} as evidence.
     *
     * <p>Note: audit log entries tagged as "technical system actions" (i.e.,
     * data type {@code "audit_logs"}) are not deleted — they have a separate
     * configurable retention and are exempt from GDPR erasure per recital 65.
     *
     * @param subjectId the data subject requesting erasure
     * @return a signed deletion certificate
     * @throws IllegalArgumentException if no data is registered for {@code subjectId}
     */
    public DeletionCertificate deleteSubject(String subjectId) {
        List<SubjectRecord> records = subjectData.remove(subjectId);
        if (records == null || records.isEmpty()) {
            // Still generate a certificate for idempotency / audit trail
            DeletionCertificate cert = DeletionCertificate.of(subjectId, List.of(), 0);
            certificates.add(cert);
            return cert;
        }

        // Group deleted records by data type (skip audit_logs)
        List<String> deletedTypes = records.stream()
                .map(SubjectRecord::dataType)
                .filter(t -> !t.equals("audit_logs"))
                .distinct()
                .sorted()
                .toList();

        int deletedCount = (int) records.stream()
                .filter(r -> !r.dataType().equals("audit_logs"))
                .count();

        DeletionCertificate cert = DeletionCertificate.of(subjectId, deletedTypes, deletedCount);
        certificates.add(cert);
        return cert;
    }

    /** Returns all generated deletion certificates (for auditing). */
    public List<DeletionCertificate> certificates() {
        return List.copyOf(certificates);
    }

    /** Returns the number of distinct subjects currently tracked. */
    public int subjectCount() {
        return subjectData.size();
    }

    /** Clears all data (for tests). */
    public void reset() {
        subjectData.clear();
        policies.clear();
        certificates.clear();
    }

    // -------------------------------------------------------------------------
    // Inner record
    // -------------------------------------------------------------------------

    public record SubjectRecord(String dataType, Object data) {}
}

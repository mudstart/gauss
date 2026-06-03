package io.gauss.augur.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link TokenUsageTracker} and {@link LLMCostEstimator}.
 * Covers HU-058 acceptance criteria.
 */
class TokenUsageTrackerTest {

    private TokenUsageTracker   tracker;
    private LLMCostEstimator    estimator;

    @BeforeEach
    void setUp() {
        estimator = new LLMCostEstimator();
        tracker   = new TokenUsageTracker(estimator);
    }

    // -------------------------------------------------------------------------
    // TokenUsage record
    // -------------------------------------------------------------------------

    @Test
    void tokenUsage_totalTokens_sumOfInputAndOutput() {
        TokenUsage u = TokenUsage.of(500, 200, "openai", "gpt-4o", "ep");
        assertThat(u.totalTokens()).isEqualTo(700);
    }

    @Test
    void tokenUsage_of_timestampIsSet() {
        assertThat(TokenUsage.of(1, 1, "openai", "gpt-4o", "ep").timestamp())
                .isNotNull();
    }

    // -------------------------------------------------------------------------
    // LLMCostEstimator
    // -------------------------------------------------------------------------

    @Test
    void estimator_gpt4o_hasKnownPrice() {
        assertThat(estimator.hasPricing("openai", "gpt-4o")).isTrue();
    }

    @Test
    void estimator_ollamaModel_returnsZeroCost() {
        TokenUsage u = TokenUsage.of(1000, 500, "ollama", "llama3", "ep");
        assertThat(estimator.estimateCostUsd(u)).isZero();
    }

    @Test
    void estimator_gpt4oMini_correctInputCost() {
        // $0.15 per 1M input tokens → 1M tokens = $0.15
        TokenUsage u = TokenUsage.of(1_000_000, 0, "openai", "gpt-4o-mini", "ep");
        assertThat(estimator.estimateCostUsd(u)).isCloseTo(0.15, within(0.001));
    }

    @Test
    void estimator_gpt4oMini_correctOutputCost() {
        // $0.60 per 1M output tokens → 1M tokens = $0.60
        TokenUsage u = TokenUsage.of(0, 1_000_000, "openai", "gpt-4o-mini", "ep");
        assertThat(estimator.estimateCostUsd(u)).isCloseTo(0.60, within(0.001));
    }

    @Test
    void estimator_unknownModel_returnsZero() {
        assertThat(estimator.hasPricing("unknown", "model-x")).isFalse();
        TokenUsage u = TokenUsage.of(500, 200, "unknown", "model-x", "ep");
        assertThat(estimator.estimateCostUsd(u)).isZero();
    }

    @Test
    void estimator_caseInsensitive() {
        assertThat(estimator.hasPricing("OpenAI", "GPT-4O")).isTrue();
    }

    // -------------------------------------------------------------------------
    // TokenUsageTracker — recording
    // -------------------------------------------------------------------------

    @Test
    void record_incrementsTotalCallCount() {
        tracker.record(TokenUsage.of(100, 50, "openai", "gpt-4o", "ep"));
        assertThat(tracker.totalCallCount()).isEqualTo(1);
    }

    @Test
    void findByEndpoint_returnsOnlyMatchingEndpoint() {
        tracker.record(TokenUsage.of(100, 50, "openai", "gpt-4o", "chat"));
        tracker.record(TokenUsage.of(200, 80, "openai", "gpt-4o", "search"));
        assertThat(tracker.findByEndpoint("chat")).hasSize(1);
    }

    @Test
    void findAll_returnsAllRecords() {
        tracker.record(TokenUsage.of(100, 50, "openai", "gpt-4o", "ep1"));
        tracker.record(TokenUsage.of(200, 80, "openai", "gpt-4o", "ep2"));
        assertThat(tracker.findAll()).hasSize(2);
    }

    // -------------------------------------------------------------------------
    // TokenUsageTracker — aggregation
    // -------------------------------------------------------------------------

    @Test
    void totalInputTokens_sumsByEndpoint() {
        tracker.record(TokenUsage.of(100, 0, "openai", "gpt-4o", "chat"));
        tracker.record(TokenUsage.of(200, 0, "openai", "gpt-4o", "chat"));
        tracker.record(TokenUsage.of(999, 0, "openai", "gpt-4o", "other"));
        assertThat(tracker.totalInputTokens("chat")).isEqualTo(300);
    }

    @Test
    void totalOutputTokens_sumsByEndpoint() {
        tracker.record(TokenUsage.of(0, 50, "openai", "gpt-4o", "chat"));
        tracker.record(TokenUsage.of(0, 70, "openai", "gpt-4o", "chat"));
        assertThat(tracker.totalOutputTokens("chat")).isEqualTo(120);
    }

    @Test
    void totalTokens_sumsBothDirections() {
        tracker.record(TokenUsage.of(100, 50, "openai", "gpt-4o", "ep"));
        assertThat(tracker.totalTokens("ep")).isEqualTo(150);
    }

    @Test
    void callCount_countsPerEndpoint() {
        tracker.record(TokenUsage.of(10, 5, "openai", "gpt-4o", "ep"));
        tracker.record(TokenUsage.of(10, 5, "openai", "gpt-4o", "ep"));
        tracker.record(TokenUsage.of(10, 5, "openai", "gpt-4o", "other"));
        assertThat(tracker.callCount("ep")).isEqualTo(2);
    }

    @Test
    void totalCostUsd_calculatesCorrectly() {
        // 1M input tokens with gpt-4o-mini = $0.15
        tracker.record(TokenUsage.of(1_000_000, 0, "openai", "gpt-4o-mini", "ep"));
        assertThat(tracker.totalCostUsd("ep")).isCloseTo(0.15, within(0.001));
    }

    @Test
    void totalCostUsd_zeroForLocalModels() {
        tracker.record(TokenUsage.of(50_000, 20_000, "ollama", "mistral", "ep"));
        assertThat(tracker.totalCostUsd("ep")).isZero();
    }

    // -------------------------------------------------------------------------
    // Budget alerts
    // -------------------------------------------------------------------------

    @Test
    void budgetAlert_notTriggered_belowThreshold() {
        // cost ≈ $0.15, budget = $10, threshold = 80%
        tracker.record(TokenUsage.of(1_000_000, 0, "openai", "gpt-4o-mini", "ep"));
        assertThat(tracker.isBudgetAlertTriggered("ep", 10.0, 0.80)).isFalse();
    }

    @Test
    void budgetAlert_triggered_aboveThreshold() {
        // cost ≈ $0.15 per 1M input tokens; budget = $0.10, threshold = 80% → alert at $0.08
        tracker.record(TokenUsage.of(1_000_000, 0, "openai", "gpt-4o-mini", "ep"));
        assertThat(tracker.isBudgetAlertTriggered("ep", 0.10, 0.80)).isTrue();
    }

    // -------------------------------------------------------------------------
    // Clear
    // -------------------------------------------------------------------------

    @Test
    void clear_removesAllRecords() {
        tracker.record(TokenUsage.of(100, 50, "openai", "gpt-4o", "ep"));
        tracker.clear();
        assertThat(tracker.totalCallCount()).isZero();
        assertThat(tracker.findAll()).isEmpty();
    }
}

package io.gauss.augur.llm;

import java.util.Map;

/**
 * Estimates the monetary cost (USD) of an LLM call based on published
 * per-token prices (HU-058).
 *
 * <p>Prices are expressed in USD per 1,000,000 tokens.  The lookup key is
 * {@code "provider/model"} (lower-case).  Unknown provider/model combinations
 * return {@code 0.0} (no cost estimate available), which is the correct
 * behaviour for local inference engines such as Ollama.
 *
 * <p>Prices are approximate and are updated periodically.  For billing-grade
 * accuracy, integrate directly with the provider's usage API.
 */
public final class LLMCostEstimator {

    /**
     * Price table: key = {@code "provider/model"},
     * value = {@code [inputPricePerMToken, outputPricePerMToken]} in USD.
     */
    private static final Map<String, double[]> PRICES = Map.ofEntries(
            Map.entry("openai/gpt-4o",          new double[]{  5.00,  15.00 }),
            Map.entry("openai/gpt-4o-mini",     new double[]{  0.15,   0.60 }),
            Map.entry("openai/gpt-4-turbo",     new double[]{ 10.00,  30.00 }),
            Map.entry("openai/gpt-3.5-turbo",   new double[]{  0.50,   1.50 }),
            Map.entry("anthropic/claude-3-opus", new double[]{ 15.00,  75.00 }),
            Map.entry("anthropic/claude-3-sonnet",new double[]{ 3.00,  15.00 }),
            Map.entry("anthropic/claude-3-haiku",new double[]{  0.25,   1.25 }),
            Map.entry("google/gemini-1.5-pro",  new double[]{  3.50,  10.50 }),
            Map.entry("google/gemini-1.5-flash",new double[]{  0.075,  0.30 })
    );

    // Ollama and other local providers: cost = 0 (intentionally absent)

    // -------------------------------------------------------------------------

    /**
     * Estimates the cost of a {@link TokenUsage} record.
     *
     * <p>Returns {@code 0.0} if the provider/model combination is unknown
     * (e.g., local Ollama models).
     *
     * @param usage the usage record to price
     * @return estimated cost in USD
     */
    public double estimateCostUsd(TokenUsage usage) {
        String key = usage.provider().toLowerCase() + "/" + usage.model().toLowerCase();
        double[] prices = PRICES.get(key);
        if (prices == null) return 0.0;
        return (usage.inputTokens()  * prices[0]
              + usage.outputTokens() * prices[1]) / 1_000_000.0;
    }

    /**
     * Returns {@code true} if a cost estimate is available for the given
     * provider/model combination.
     */
    public boolean hasPricing(String provider, String model) {
        String key = provider.toLowerCase() + "/" + model.toLowerCase();
        return PRICES.containsKey(key);
    }

    /** Returns the input price per 1M tokens for the given provider/model, or 0. */
    public double inputPricePerMToken(String provider, String model) {
        double[] prices = PRICES.get(provider.toLowerCase() + "/" + model.toLowerCase());
        return prices == null ? 0.0 : prices[0];
    }

    /** Returns the output price per 1M tokens for the given provider/model, or 0. */
    public double outputPricePerMToken(String provider, String model) {
        double[] prices = PRICES.get(provider.toLowerCase() + "/" + model.toLowerCase());
        return prices == null ? 0.0 : prices[1];
    }
}

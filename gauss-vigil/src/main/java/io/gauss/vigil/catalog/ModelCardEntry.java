package io.gauss.vigil.catalog;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable, structured documentation snapshot for a registered model (HU-055).
 *
 * <p>A Model Card captures everything a governance reviewer needs to assess
 * whether a model is suitable for a given use case: what it does, how it was
 * trained, its known limitations, and its evaluation metrics at the time it
 * was promoted.
 *
 * <p>Cards are produced by {@link ModelCardService} and can be exported as
 * JSON via {@link ModelCardService#toJson(ModelCardEntry)}.
 */
public record ModelCardEntry(
        String              modelId,
        String              modelName,
        String              description,
        String              intendedUse,
        String              limitations,
        String              trainedOn,
        String              cardVersion,
        Map<String, Double> metrics,
        String              experimentId,
        Instant             generatedAt
) {

    // -------------------------------------------------------------------------
    // Compact canonical constructor — defensive copy of metrics
    // -------------------------------------------------------------------------

    public ModelCardEntry {
        metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder(String modelId, String modelName) {
        return new Builder(modelId, modelName);
    }

    public static final class Builder {

        private final String modelId;
        private final String modelName;
        private String              description   = "";
        private String              intendedUse   = "";
        private String              limitations   = "";
        private String              trainedOn     = "";
        private String              cardVersion   = "1.0";
        private Map<String, Double> metrics       = Map.of();
        private String              experimentId  = null;

        private Builder(String modelId, String modelName) {
            this.modelId    = modelId;
            this.modelName  = modelName;
        }

        public Builder description(String v)              { description  = v; return this; }
        public Builder intendedUse(String v)              { intendedUse  = v; return this; }
        public Builder limitations(String v)              { limitations  = v; return this; }
        public Builder trainedOn(String v)                { trainedOn    = v; return this; }
        public Builder cardVersion(String v)              { cardVersion  = v; return this; }
        public Builder metrics(Map<String, Double> v)     { metrics      = v; return this; }
        public Builder experimentId(String v)             { experimentId = v; return this; }

        public ModelCardEntry build() {
            return new ModelCardEntry(
                    modelId, modelName, description, intendedUse, limitations,
                    trainedOn, cardVersion, metrics, experimentId, Instant.now());
        }
    }
}

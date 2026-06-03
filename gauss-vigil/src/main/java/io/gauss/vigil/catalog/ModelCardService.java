package io.gauss.vigil.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.gauss.core.annotation.ModelCard;
import io.gauss.vigil.experiment.ExperimentMetric;
import io.gauss.vigil.experiment.ExperimentStore;
import io.gauss.vigil.registry.ModelRegistration;
import io.gauss.vigil.registry.ModelRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds and exports {@link ModelCardEntry} objects for registered models (HU-055).
 *
 * <p>A Model Card is assembled from three sources in priority order:
 * <ol>
 *   <li>The {@link ModelCard} annotation on the model's training class (if provided).</li>
 *   <li>Evaluation metrics from the associated {@link io.gauss.vigil.experiment.ExperimentRun}
 *       (if an {@link ExperimentStore} is wired and an {@code experimentId} is set).</li>
 *   <li>Metadata already captured in the {@link ModelRegistration} (name, id, etc.).</li>
 * </ol>
 *
 * <p>Usage:
 * <pre>{@code
 * ModelCardService svc = new ModelCardService(experimentStore);
 *
 * // Build a card for one registration using the @ModelCard annotation on ChurnPipeline
 * ModelCardEntry card = svc.build(registration, ChurnPipeline.class);
 *
 * // Serialize to JSON (Hugging Face Model Cards-compatible structure)
 * String json = svc.toJson(card);
 *
 * // Build cards for all currently registered models
 * List<ModelCardEntry> all = svc.buildAll(ChurnPipeline.class);
 * }</pre>
 */
public final class ModelCardService {

    private final ExperimentStore experimentStore;

    private static final ObjectMapper MAPPER;

    static {
        MAPPER = new ObjectMapper();
        MAPPER.registerModule(new JavaTimeModule());
        MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // -------------------------------------------------------------------------

    /** Creates a service without experiment-store backing (metrics will be empty). */
    public ModelCardService() {
        this(null);
    }

    /** Creates a service that enriches cards with metrics from {@code experimentStore}. */
    public ModelCardService(ExperimentStore experimentStore) {
        this.experimentStore = experimentStore;
    }

    // -------------------------------------------------------------------------
    // Build
    // -------------------------------------------------------------------------

    /**
     * Builds a {@link ModelCardEntry} for {@code registration}, enriching it
     * with data from the {@link ModelCard} annotation on {@code modelClass}.
     *
     * @param registration the model whose card is being built
     * @param modelClass   the training class bearing {@link ModelCard} (may be {@code null})
     * @return a fully populated entry
     */
    public ModelCardEntry build(ModelRegistration registration, Class<?> modelClass) {
        ModelCard ann = modelClass != null ? modelClass.getAnnotation(ModelCard.class) : null;

        ModelCardEntry.Builder builder = ModelCardEntry.builder(registration.id(), registration.modelName());

        if (ann != null) {
            builder.description(ann.description())
                   .intendedUse(ann.intendedUse())
                   .limitations(ann.limitations())
                   .trainedOn(ann.trainedOn())
                   .cardVersion(ann.version());
        }

        // Enrich with experiment metrics
        if (experimentStore != null && registration.experimentId() != null) {
            experimentStore.findById(registration.experimentId()).ifPresent(run -> {
                Map<String, Double> metrics = new LinkedHashMap<>();
                run.metrics().forEach(m -> metrics.put(m.name(), m.value()));
                builder.metrics(metrics);
            });
        }

        if (registration.experimentId() != null) {
            builder.experimentId(registration.experimentId());
        }

        return builder.build();
    }

    /**
     * Builds a card for {@code registration} without a model class (no annotation
     * enrichment; only experiment metrics if the store is set).
     */
    public ModelCardEntry build(ModelRegistration registration) {
        return build(registration, null);
    }

    /**
     * Builds a card for every currently registered model, all sharing the same
     * {@code modelClass} for annotation enrichment.
     *
     * @param modelClass the training class to scan for {@link ModelCard}
     * @return list of cards in registration order
     */
    public List<ModelCardEntry> buildAll(Class<?> modelClass) {
        return ModelRegistry.findAll().stream()
                .map(reg -> build(reg, modelClass))
                .toList();
    }

    // -------------------------------------------------------------------------
    // Export
    // -------------------------------------------------------------------------

    /**
     * Serializes a card to a compact JSON string following the
     * Hugging Face Model Cards schema conventions.
     *
     * @param card the card to serialize
     * @return JSON representation
     * @throws RuntimeException wrapping any Jackson serialization error
     */
    public String toJson(ModelCardEntry card) {
        try {
            return MAPPER.writeValueAsString(card);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize ModelCardEntry to JSON", e);
        }
    }
}

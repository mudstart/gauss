package io.gauss.flume.scanner;

import io.gauss.core.annotation.DataPipeline;
import io.gauss.core.annotation.Ingest;
import io.gauss.core.annotation.Transform;
import io.gauss.flume.model.PipelineDescriptor;
import io.gauss.flume.model.PipelineStep;
import io.gauss.flume.model.StepType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link PipelineScanner}.
 * Covers HU-011 acceptance criteria 1–4.
 */
class PipelineScannerTest {

    // -------------------------------------------------------------------------
    // Fixture pipeline classes
    // -------------------------------------------------------------------------

    /** Simple types used as pipeline data to avoid erasure ambiguity. */
    static class RawData    {}
    static class CleanData  {}
    static class Features   {}

    @DataPipeline("simple-pipeline")
    static class SimplePipeline {
        @Ingest(source = "file:///data/raw.csv")
        public RawData load() { return new RawData(); }

        @Transform("clean")
        public CleanData clean(RawData raw) { return new CleanData(); }

        @Transform
        public Features engineer(CleanData clean) { return new Features(); }
    }

    @DataPipeline("no-transform")
    static class IngestOnlyPipeline {
        @Ingest(source = "jdbc://ds/table")
        public RawData load() { return new RawData(); }
    }

    /** Not annotated — used to verify rejection. */
    static class NotAPipeline {}

    @DataPipeline("cyclic")
    static class CyclicPipeline {
        @Transform
        public RawData a(CleanData c) { return new RawData(); }

        @Transform
        public CleanData b(RawData r) { return new CleanData(); }
    }

    // -------------------------------------------------------------------------
    // AC-1: class annotated with @DataPipeline is recognised
    // -------------------------------------------------------------------------

    @Test
    void scan_recognises_dataPipelineAnnotation() {
        PipelineDescriptor desc = PipelineScanner.scan(SimplePipeline.class);
        assertThat(desc.name()).isEqualTo("simple-pipeline");
        assertThat(desc.pipelineClass()).isEqualTo(SimplePipeline.class);
    }

    @Test
    void scan_throwsForUnannotatedClass() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PipelineScanner.scan(NotAPipeline.class))
                .withMessageContaining("@DataPipeline");
    }

    // -------------------------------------------------------------------------
    // AC-2: @Ingest methods are pipeline entry-points
    // -------------------------------------------------------------------------

    @Test
    void ingestStep_identifiedAsEntryPoint() {
        PipelineDescriptor desc = PipelineScanner.scan(SimplePipeline.class);
        PipelineStep ingest = desc.ingestStep();

        assertThat(ingest.type()).isEqualTo(StepType.INGEST);
        assertThat(ingest.source()).isEqualTo("file:///data/raw.csv");
        assertThat(ingest.inputType()).isNull();
        assertThat(ingest.outputType()).isEqualTo(RawData.class);
    }

    @Test
    void ingestOnlyPipeline_hasNoTransforms() {
        PipelineDescriptor desc = PipelineScanner.scan(IngestOnlyPipeline.class);
        assertThat(desc.transformSteps()).isEmpty();
        assertThat(desc.ingestStep().source()).isEqualTo("jdbc://ds/table");
    }

    // -------------------------------------------------------------------------
    // AC-3: @Transform methods receive the output of the preceding step
    // -------------------------------------------------------------------------

    @Test
    void transformSteps_recordCorrectInputAndOutputTypes() {
        PipelineDescriptor desc = PipelineScanner.scan(SimplePipeline.class);
        List<PipelineStep> transforms = desc.transformSteps();

        assertThat(transforms).hasSize(2);

        // "clean" step: RawData in → CleanData out
        PipelineStep clean = transforms.stream()
                .filter(s -> s.name().equals("clean")).findFirst().orElseThrow();
        assertThat(clean.inputType()).isEqualTo(RawData.class);
        assertThat(clean.outputType()).isEqualTo(CleanData.class);

        // "engineer" step: CleanData in → Features out
        PipelineStep engineer = transforms.stream()
                .filter(s -> s.name().equals("engineer")).findFirst().orElseThrow();
        assertThat(engineer.inputType()).isEqualTo(CleanData.class);
        assertThat(engineer.outputType()).isEqualTo(Features.class);
    }

    @Test
    void transform_nameDefaultsToMethodName_whenAnnotationValueIsBlank() {
        PipelineDescriptor desc = PipelineScanner.scan(SimplePipeline.class);
        assertThat(desc.transformSteps())
                .extracting(PipelineStep::name)
                .contains("engineer");        // method name used because @Transform has no value
    }

    // -------------------------------------------------------------------------
    // AC-4: execution order is inferred from the dependency graph
    // -------------------------------------------------------------------------

    @Test
    void steps_orderedTopologicallyByTypeDependencies() {
        PipelineDescriptor desc = PipelineScanner.scan(SimplePipeline.class);
        List<PipelineStep> steps = desc.steps();

        // load must come before clean, clean before engineer
        int loadIdx    = indexOfStep(steps, "load");
        int cleanIdx   = indexOfStep(steps, "clean");
        int engineerIdx = indexOfStep(steps, "engineer");

        assertThat(loadIdx).isLessThan(cleanIdx);
        assertThat(cleanIdx).isLessThan(engineerIdx);
    }

    @Test
    void scan_throwsOnCyclicDependencies() {
        assertThatIllegalStateException()
                .isThrownBy(() -> PipelineScanner.scan(CyclicPipeline.class))
                .withMessageContaining("Cycle");
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private static int indexOfStep(List<PipelineStep> steps, String name) {
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).name().equals(name)) return i;
        }
        throw new AssertionError("Step not found: " + name);
    }
}

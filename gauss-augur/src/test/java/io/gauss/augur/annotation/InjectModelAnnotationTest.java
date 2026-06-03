package io.gauss.augur.annotation;

import io.gauss.augur.model.OnnxModel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.*;

/**
 * Verifies the {@link InjectModel} annotation structure and retention.
 */
class InjectModelAnnotationTest {

    static class SampleService {
        @InjectModel("models/churn.onnx")
        OnnxModel model;

        @InjectModel("models/nlp.onnx")
        OnnxModel nlpModel;
    }

    @Test
    void annotation_retainedAtRuntime() throws NoSuchFieldException {
        Field field = SampleService.class.getDeclaredField("model");
        assertThat(field.isAnnotationPresent(InjectModel.class)).isTrue();
    }

    @Test
    void annotation_storesModelPath() throws NoSuchFieldException {
        Field field = SampleService.class.getDeclaredField("model");
        InjectModel ann = field.getAnnotation(InjectModel.class);
        assertThat(ann.value()).isEqualTo("models/churn.onnx");
    }

    @Test
    void annotation_canBeAppliedToMultipleFields() throws NoSuchFieldException {
        Field nlp = SampleService.class.getDeclaredField("nlpModel");
        assertThat(nlp.getAnnotation(InjectModel.class).value())
                .isEqualTo("models/nlp.onnx");
    }

    @Test
    void annotation_targetsFieldsOnly() {
        var targets = InjectModel.class.getAnnotation(
                java.lang.annotation.Target.class);
        assertThat(targets.value())
                .containsExactly(java.lang.annotation.ElementType.FIELD);
    }
}

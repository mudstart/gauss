package io.gauss.augur.onnx;

import ai.onnxruntime.*;
import io.gauss.augur.model.ModelDescriptor;
import io.gauss.augur.model.OnnxInferenceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OnnxModelAdapter} using a mocked {@link OrtSession}
 * and static mocking of {@link OnnxTensor#createTensor}.
 */
@ExtendWith(MockitoExtension.class)
class OnnxModelAdapterTest {

    @Mock OrtEnvironment   env;
    @Mock OrtSession       session;
    @Mock OrtSession.Result ortResult;
    @Mock OnnxValue        outputValue;

    private static final ModelDescriptor DESC = new ModelDescriptor(
            "churn", "models/churn.onnx",
            List.of("input"), List.of("output"));

    // -------------------------------------------------------------------------
    // descriptor()
    // -------------------------------------------------------------------------

    @Test
    void descriptor_returnsConstructedMetadata() {
        OnnxModelAdapter adapter = new OnnxModelAdapter(env, session, DESC);
        assertThat(adapter.descriptor()).isSameAs(DESC);
        assertThat(adapter.descriptor().name()).isEqualTo("churn");
        assertThat(adapter.descriptor().inputNames()).containsExactly("input");
    }

    // -------------------------------------------------------------------------
    // run()
    // -------------------------------------------------------------------------

    @Test
    void run_invokesSessionAndReturnsOutputMap() throws OrtException {
        float[] outputData = {0.9f, 0.1f};
        OnnxTensor inputTensor = mock(OnnxTensor.class);

        try (MockedStatic<OnnxTensor> staticTensor = mockStatic(OnnxTensor.class)) {
            staticTensor.when(() -> OnnxTensor.createTensor(
                            any(OrtEnvironment.class), any(float[].class)))
                        .thenReturn(inputTensor);

            when(session.run(any())).thenReturn(ortResult);
            when(ortResult.iterator()).thenReturn(
                    List.of(Map.entry("output", outputValue)).iterator());
            when(outputValue.getValue()).thenReturn(outputData);

            OnnxModelAdapter adapter = new OnnxModelAdapter(env, session, DESC);
            Map<String, Object> result = adapter.run(Map.of("input", new float[]{1f, 2f}));

            assertThat(result).containsKey("output");
            assertThat(result.get("output")).isEqualTo(outputData);
        }
    }

    @Test
    void run_throwsOnnxInferenceExceptionOnOrtException() throws OrtException {
        OnnxTensor inputTensor = mock(OnnxTensor.class);

        try (MockedStatic<OnnxTensor> staticTensor = mockStatic(OnnxTensor.class)) {
            staticTensor.when(() -> OnnxTensor.createTensor(
                            any(OrtEnvironment.class), any(float[].class)))
                        .thenReturn(inputTensor);

            when(session.run(any())).thenThrow(new OrtException("internal error"));

            OnnxModelAdapter adapter = new OnnxModelAdapter(env, session, DESC);

            assertThatExceptionOfType(OnnxInferenceException.class)
                    .isThrownBy(() -> adapter.run(Map.of("input", new float[]{1f})))
                    .withMessageContaining("Inference failed")
                    .withCauseInstanceOf(OrtException.class);
        }
    }

    @Test
    void run_throwsForUnsupportedInputType() {
        OnnxModelAdapter adapter = new OnnxModelAdapter(env, session, DESC);

        assertThatExceptionOfType(OnnxInferenceException.class)
                .isThrownBy(() -> adapter.run(Map.of("input", "a string")))
                .withMessageContaining("Unsupported tensor input type");
    }

    // -------------------------------------------------------------------------
    // close()
    // -------------------------------------------------------------------------

    @Test
    void close_closesUnderlyingSession() throws OrtException {
        OnnxModelAdapter adapter = new OnnxModelAdapter(env, session, DESC);
        adapter.close();
        verify(session).close();
    }
}

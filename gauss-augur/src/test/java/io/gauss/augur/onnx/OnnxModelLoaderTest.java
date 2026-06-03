package io.gauss.augur.onnx;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link OnnxModelLoader} — covers path support and name derivation.
 * Full load() is not tested here because it requires a real .onnx file on the classpath.
 */
class OnnxModelLoaderTest {

    @Test
    void supports_onnxExtension_returnsTrue() {
        OnnxModelLoader loader = new OnnxModelLoader();
        assertThat(loader.supports("models/churn.onnx")).isTrue();
    }

    @Test
    void supports_nonOnnxExtension_returnsFalse() {
        OnnxModelLoader loader = new OnnxModelLoader();
        assertThat(loader.supports("models/churn.pkl")).isFalse();
    }

    @Test
    void supports_nullPath_returnsFalse() {
        OnnxModelLoader loader = new OnnxModelLoader();
        assertThat(loader.supports(null)).isFalse();
    }

    @Test
    void deriveModelName_stripsPathAndExtension() {
        assertThat(OnnxModelLoader.deriveModelName("models/churn.onnx"))
                .isEqualTo("churn");
    }

    @Test
    void deriveModelName_handlesRootLevelPath() {
        assertThat(OnnxModelLoader.deriveModelName("my-model.onnx"))
                .isEqualTo("my-model");
    }

    @Test
    void deriveModelName_handlesPathWithoutExtension() {
        assertThat(OnnxModelLoader.deriveModelName("models/raw-model"))
                .isEqualTo("raw-model");
    }

    @Test
    void load_throwsIOException_whenClasspathResourceMissing() {
        OnnxModelLoader loader = new OnnxModelLoader();
        assertThatIOException()
                .isThrownBy(() -> loader.load("models/does-not-exist.onnx"))
                .withMessageContaining("not found on classpath");
    }
}

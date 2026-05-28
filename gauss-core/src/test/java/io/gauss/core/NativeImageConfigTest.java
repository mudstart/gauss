package io.gauss.core;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers HU-047: GraalVM native-image configuration files are present and valid.
 */
class NativeImageConfigTest {

    private static final String BASE =
            "META-INF/native-image/io.gauss/gauss-core/";

    @Test
    void reflectConfig_exists() {
        assertThat(resource(BASE + "reflect-config.json")).isNotNull();
    }

    @Test
    void reflectConfig_containsMLEndpoint() throws Exception {
        String json = readResource(BASE + "reflect-config.json");
        assertThat(json).contains("io.gauss.core.annotation.MLEndpoint");
    }

    @Test
    void reflectConfig_containsGaussConfig() throws Exception {
        String json = readResource(BASE + "reflect-config.json");
        assertThat(json).contains("io.gauss.core.config.GaussConfig");
    }

    @Test
    void reflectConfig_isValidJsonArray() throws Exception {
        String json = readResource(BASE + "reflect-config.json").strip();
        assertThat(json).startsWith("[");
        assertThat(json).endsWith("]");
    }

    @Test
    void resourceConfig_exists() {
        assertThat(resource(BASE + "resource-config.json")).isNotNull();
    }

    @Test
    void resourceConfig_includesServiceLoaderFile() throws Exception {
        String json = readResource(BASE + "resource-config.json");
        assertThat(json).contains("META-INF/services/io.gauss.core.spi.RuntimeAdapter");
    }

    @Test
    void nativeImageProperties_exists() {
        assertThat(resource(BASE + "native-image.properties")).isNotNull();
    }

    @Test
    void nativeImageProperties_containsInitializeAtBuildTime() throws Exception {
        String props = readResource(BASE + "native-image.properties");
        assertThat(props).contains("--initialize-at-build-time");
    }

    // -----------------------------------------------------------------------

    private static InputStream resource(String path) {
        return NativeImageConfigTest.class.getClassLoader().getResourceAsStream(path);
    }

    private static String readResource(String path) throws Exception {
        try (InputStream is = resource(path)) {
            assertThat(is).as("Resource not found: " + path).isNotNull();
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

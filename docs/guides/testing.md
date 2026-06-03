---
layout: default
title: Testing
parent: Guías
nav_order: 1
---

# Testing en Gauss
{: .no_toc }

<details open markdown="block">
<summary>Contenido</summary>
{: .text-delta }
1. TOC
{:toc}
</details>

Cada módulo incluye sus propias utilidades de test. Ninguna extensión JUnit 5
de Gauss requiere arrancar un servidor ni un contexto CDI/Spring completo.

---

## `GaussPipelineExtension` — Tests de pipelines

```java
@ExtendWith(GaussPipelineExtension.class)
class ChurnPipelineTest {

    @Test
    void engineer_computesCorrectFeatures(PipelineTestRunner runner) {
        PipelineTestResult result = runner
            .withMockSource("customers",    List.of(new Customer("c1", 35)))
            .withMockSource("transactions", List.of(new Transaction("c1", 100.0)))
            .run("churn-features", new ChurnFeaturePipeline());

        assertThat(result.outputOf("engineer")).isNotNull();
    }
}
```

**`@MockSource`** sustituye el método `@Ingest` real con datos en memoria.

---

## `GaussModelExtension` — Tests de endpoints

```java
@ExtendWith(GaussModelExtension.class)
class ChurnEndpointTest {

    @Test
    void predict_returnsMockScore(@MockModels MockModels models) {
        models.register("models/churn.onnx", input -> new float[]{0.87f});

        ChurnEndpoint endpoint = new ChurnEndpoint();
        assertThat(endpoint.predict(new CustomerInput("c42", 35, "a@b.com")))
            .isCloseTo(0.87, within(0.001));
    }
}
```

---

## `GaussFeatureExtension` — Tests del feature store

```java
@ExtendWith(GaussFeatureExtension.class)
class CustomerFeaturesTest {

    @Test
    void txCount_cachedAfterFirstCall(InMemoryFeatureStore store, TestClock clock) {
        CustomerFeatures bean = new CustomerFeatures();
        store.getAll("c-42", bean, CustomerFeatures.class);
        store.getAll("c-42", bean, CustomerFeatures.class);  // segunda llamada

        assertThat(store.computeCount("txCount30d")).isEqualTo(1);  // calculado 1 sola vez
    }

    @Test
    void feature_expiredAfterTtl(InMemoryFeatureStore store, TestClock clock) {
        CustomerFeatures bean = new CustomerFeatures();
        store.getAll("c-42", bean, CustomerFeatures.class);

        clock.advance(Duration.ofHours(2));  // superar TTL de 1h

        FeatureDescriptor desc = FeatureClass.scan(CustomerFeatures.class)
                                             .find("txCount30d").orElseThrow();
        assertThat(store.get("c-42", desc)).isEmpty();
    }
}
```

---

## `TestClock` — Control del tiempo

```java
TestClock clock = new TestClock();              // comienza en Instant.now()
clock.advance(Duration.ofHours(2));            // avanzar 2 horas
clock.reset(Instant.parse("2026-01-01T00:00:00Z")); // fijar fecha concreta
clock.advance(Duration.ofSeconds(-1));         // lanza IllegalArgumentException
```

---

## `InMemorySpanExporter` — Tests de trazas

```java
InMemorySpanExporter exporter = new InMemorySpanExporter();
GaussTracer tracer = new GaussTracer(exporter);

try (var span = tracer.startSpan("predict")) {
    span.setAttribute("endpoint", "churn");
}

assertThat(exporter.spans()).hasSize(1);
assertThat(exporter.findByName("predict")).isPresent();
assertThat(exporter.spans().get(0).durationMs()).isGreaterThanOrEqualTo(0);
```

---

## `InMemorySecretProvider` — Tests con secretos

```java
InMemorySecretProvider secrets = new InMemorySecretProvider();
secrets.set("openai-api-key", "sk-test-1234");

SecretProviderRegistry registry = new SecretProviderRegistry(secrets);
assertThat(registry.getRequired("openai-api-key")).isEqualTo("sk-test-1234");
```

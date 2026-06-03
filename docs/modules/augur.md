---
layout: default
title: gauss-augur
parent: Módulos
nav_order: 4
---

# gauss-augur — Serving de modelos
{: .no_toc }

<details open markdown="block">
<summary>Contenido</summary>
{: .text-delta }
1. TOC
{:toc}
</details>

El módulo de serving agrupa todas las capacidades de inferencia: ONNX, LLMs,
caché, circuit breaker, versionado A/B, batch prediction, explicabilidad SHAP
y detección de drift.

---

## Exponer un modelo con `@MLEndpoint`

```java
@MLEndpoint
@LatencySLO(p99 = "50ms", p95 = "20ms")
@DriftMonitor(threshold = 0.1, metric = "PSI", sampleSize = 500)
public class ChurnEndpoint {

    @InjectModel("models/churn-v2.onnx")
    private OnnxModel model;

    @CachedPrediction(ttl = "5m")
    @CircuitBreaker(threshold = 5, delay = "30s", fallback = "safePredict")
    @Explainable(topFeatures = 5)
    public double predict(CustomerInput input) {
        return model.predict(toTensor(input))[0];
    }

    /** Fallback cuando el circuito está abierto */
    public double safePredict(CustomerInput input) { return 0.5; }
}
```

El path HTTP se deriva por convención: `ChurnEndpoint` → `/api/churn`.

---

## Inferencia ONNX

```java
@InjectModel("models/churn.onnx")
private OnnxModel model;

float[] output = model.predict(new float[]{ age, income, tenure });
double  score  = output[0];
```

- Se carga al arrancar y se cachea en memoria.
- Soporta modelos de clasificación, regresión y transformers.
- Latencia de inferencia registrada como métrica Micrometer.

---

## Integración con LLMs (LangChain4j)

```java
@LLMEndpoint(provider = "openai", model = "gpt-4o")
public String chat(String prompt) { ... }

@LLMEndpoint(provider = "ollama")   // modelo local
public Multi<String> stream(String prompt) { ... }
```

### Guardrails de seguridad (prompt injection)

```java
PromptGuardrailService guardrails = new PromptGuardrailService()
    .withPattern(new GuardrailPattern("no-pii", "\\d{3}-\\d{2}-\\d{4}", "Bloquear SSNs"));

guardrails.validate(userInput);   // lanza LLMGuardrailViolationException si es peligroso

List<GuardrailPattern> matches = guardrails.scan(userInput);  // sin lanzar
```

**Patrones built-in:** `ignore_instructions`, `system_override`, `jailbreak_roleplay`,
`delimiter_injection`, `prompt_exfiltration`.

---

## Caché de predicciones

```java
@CachedPrediction(ttl = "5m", backend = "memory")
public double predict(CustomerInput input) { ... }

@CacheEvict
public void refreshModel() { ... }   // invalida la caché
```

La clave de caché es `(endpointName, hash(input))`.

---

## Circuit breaker

| Estado | Descripción |
|--------|-------------|
| `CLOSED` | Operación normal |
| `OPEN` | `threshold` fallos seguidos → llama al fallback |
| `HALF_OPEN` | Tras `delay`, permite una llamada de prueba |

---

## Versionado A/B de modelos

```java
@ModelVersion(value = "v1", weight = 80)
@ModelVersion(value = "v2", weight = 20)
public class ChurnEndpoint { ... }
```

```java
// Actualizar pesos en caliente
router.updateWeights(List.of(
    new VersionWeight("v1", 50),
    new VersionWeight("v2", 50)
));
```

---

## Batch prediction asíncrono

```java
String jobId = tracker.submit("churn", customerIds,
    id -> model.predict(toTensor(id)), batchSize: 100);

BatchJob job = tracker.findJob(jobId).orElseThrow();
System.out.println(job.progressPercent() + "%");

tracker.cancel(jobId);  // cancelable
```

---

## Tracking de tokens LLM y control de costes

```java
tracker.record(TokenUsage.of(500, 200, "openai", "gpt-4o", "chatbot"));

double cost   = tracker.totalCostUsd("chatbot");
boolean alert = tracker.isBudgetAlertTriggered("chatbot", 10.0, 0.80);
```

**Precios incluidos:** OpenAI (GPT-4o, GPT-4o-mini), Anthropic (Claude 3), Google (Gemini 1.5).
Ollama y otros locales devuelven coste = $0.

---

## Detección de drift (PSI)

```java
drift.setReference("churn", trainingData, 10);  // 10 buckets
drift.recordObservation("churn", featureValue); // tras cada predicción

drift.evaluate("churn", 0.1, 10).ifPresent(r -> {
    if (r.alert()) log.warn(r.summary());
});
```

| PSI | Interpretación |
|-----|----------------|
| < 0.10 | Distribución estable |
| 0.10 – 0.25 | Cambio moderado |
| > 0.25 | Cambio mayor — reentrenar |

---

## Explicabilidad SHAP (Kernel SHAP)

```java
KernelShap shap = new KernelShap(backgroundData);
ExplanationResult result = shap.explain(
    inputVector,
    new String[]{"age", "income", "tenure"},
    model::predict,
    topFeatures: 5
);

result.shapValues().forEach(v ->
    System.out.printf("%s: %+.4f (rank %d)%n",
        v.featureName(), v.impact(), v.rank()));
```

Propiedad verificada: para un modelo lineal `f(x) = Σcᵢxᵢ`,
el valor SHAP de la feature `i` es exactamente `cᵢ·(xᵢ − background_i)`.

---

## SLOs de latencia

```java
@LatencySLO(p50 = "5ms", p95 = "20ms", p99 = "50ms")
public class ChurnEndpoint { ... }
```

```bash
mvn gauss:benchmark    # verifica SLOs con JMH
```

---

## Trazas OpenTelemetry

```java
GaussTracer tracer = new GaussTracer(otlpExporter);

try (var span = tracer.startSpan("predict", "SERVER")) {
    span.setAttribute("endpoint", "churn");
    Object result = predict(input);
}
// → span exportado a Jaeger / Tempo / Zipkin
```

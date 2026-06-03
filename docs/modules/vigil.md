---
layout: default
title: gauss-vigil
parent: Módulos
nav_order: 5
---

# gauss-vigil — Experiment Tracking y Model Registry
{: .no_toc }

<details open markdown="block">
<summary>Contenido</summary>
{: .text-delta }
1. TOC
{:toc}
</details>

---

## Registrar experimentos

```java
runner.run("churn-xgboost",
    new String[]{"xgboost", "churn"},
    Map.of("lr", 0.05, "depth", 6),
    ctx -> {
        XGBoostModel model = XGBoost.train(dataset, 0.05, 6);
        ctx.logMetric("auc",  model.getAuc());
        ctx.logMetric("f1",   model.getF1());
        ctx.logArtifact("confusion_matrix", model.confusionMatrix());
        return model.getAuc();
    });
```

O con la anotación (interceptor CDI):

```java
@Experiment(name = "churn-xgboost", tags = {"xgboost", "churn"})
public TrainedModel train(double lr, int depth, ExperimentContext ctx) {
    TrainedModel model = XGBoost.train(dataset, lr, depth);
    ctx.logMetric("auc", model.getAuc());
    return model;
}
```

---

## Consultar y comparar experimentos

```java
List<ExperimentRun> runs = queryService.query(
    ExperimentQuery.builder()
        .name("churn-xgboost")
        .fromDate(Instant.now().minus(Duration.ofDays(7)))
        .sortByMetric("auc")
        .pageSize(20)
        .build()
);

ExperimentDiff diff = queryService.diff("run-id-1", "run-id-2");
```

---

## Streaming de métricas en tiempo real (SSE)

```java
MetricStreamService stream = new MetricStreamService();

for (int epoch = 0; epoch < 100; epoch++) {
    stream.log(experimentId, "loss", trainEpoch(epoch), epoch);
}

// El endpoint SSE hace polling de nuevos puntos:
List<StepMetric> nuevos = stream.since(experimentId, "loss", lastStep);
```

---

## Model Registry

```java
// Registrar desde experimento
String modelId = ModelRegistry.register("churn-v2", experimentId, "models/churn.onnx");

// Promover con guardrail de calidad
ModelRegistry.promote(modelId, Stage.PRODUCTION, "alice",
    new ModelGuardrail[]{ @ModelGuardrail(metric = "auc", min = 0.90) });

// Historial de etapas
List<StageTransition> history = ModelRegistry.find(modelId)
    .orElseThrow().stageHistory();
```

**Ciclo de vida:** `STAGING` → `PRODUCTION` → `ARCHIVED`

---

## Rollback automático

```java
rollback.recordMetric(modelId, "error_rate", 0.22);

rollback.evaluate(modelId, RollbackPolicy.of("error_rate", 0.15))
        .ifPresent(event -> alertManager.send(event.summary()));
```

Límite configurable con `maxPerHour` para evitar oscilaciones.
El historial se indexa por **nombre de modelo**, no por ID de versión.

---

## Análisis estadístico A/B

```java
// Para métricas binarias (éxito/fallo)
ABTestResult r = stats.testProportions("v1", "v2", samples, 0.05);

// Para métricas continuas (latencia, AUC…)
ABTestResult r = stats.testMeans("v1", "v2", samples, 0.05);

if (r.significant()) {
    System.out.println("Ganador: " + r.recommendedWinner().orElseThrow());
    System.out.printf("IC 95%% v1: [%.3f, %.3f]%n", r.ci95LowerA(), r.ci95UpperA());
}
```

---

## Model Card

```java
@ModelCard(
    description  = "XGBoost churn prediction",
    intendedUse  = "Clientes B2C, próximos 30 días",
    limitations  = "No apto para segmento B2B",
    trainedOn    = "Transacciones 2020-2024"
)
@DataPipeline("churn-training")
public class ChurnTrainingPipeline { ... }
```

```java
ModelCardEntry card = cardService.build(registration, ChurnTrainingPipeline.class);
String json = cardService.toJson(card);  // JSON compatible con Hugging Face Model Cards
```

---

## Health checks

```java
public class FeatureStoreHealthIndicator implements HealthIndicator {
    public ComponentHealth check() {
        return featureStore.isAlive()
            ? ComponentHealth.up("feature-store")
            : ComponentHealth.down("feature-store", "connection timeout");
    }
}
```

**Agregación:** `DOWN > DEGRADED/UNKNOWN > UP`

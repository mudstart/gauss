---
layout: default
title: gauss-core
parent: Módulos
nav_order: 1
---

# gauss-core — Anotaciones fundamentales
{: .no_toc }

<details open markdown="block">
<summary>Contenido</summary>
{: .text-delta }
1. TOC
{:toc}
</details>

`gauss-core` es el único módulo sin dependencias externas. Define las 21 anotaciones del framework y registra todas las clases para reflexión en GraalVM native image.

---

## Todas las anotaciones

| Anotación | Target | Módulo responsable | Descripción |
|-----------|--------|--------------------|-------------|
| `@MLEndpoint` | TYPE | gauss-augur | Expone clase como endpoint HTTP ML |
| `@DataPipeline` | TYPE | gauss-flume | Define un pipeline de datos |
| `@Ingest` | METHOD | gauss-flume | Punto de entrada de datos |
| `@Transform` | METHOD | gauss-flume | Paso de transformación |
| `@Scheduled` | TYPE, METHOD | gauss-flume | Planifica con expresión cron |
| `@SparkExecution` | TYPE | gauss-spark | Ejecuta pipeline en Apache Spark |
| `@Feature` | METHOD | gauss-stratum | Define una feature cacheada |
| `@Experiment` | METHOD | gauss-vigil | Registra un run de experimento |
| `@ModelCard` | TYPE | gauss-vigil | Documenta un modelo ML |
| `@ModelVersion` | TYPE | gauss-augur | Versión para A/B routing |
| `@ModelGuardrail` | METHOD, TYPE | gauss-vigil | Guardrail de calidad para promoción |
| `@BatchPrediction` | METHOD | gauss-augur | Predicción asíncrona en lote |
| `@CachedPrediction` | METHOD | gauss-augur | Caché de predicciones |
| `@CacheEvict` | METHOD | gauss-augur | Invalida caché de predicciones |
| `@CircuitBreaker` | METHOD | gauss-augur | Circuit breaker en endpoint |
| `@AutoRollback` | TYPE | gauss-vigil | Rollback automático por métricas |
| `@DriftMonitor` | TYPE | gauss-augur | Detección de drift de datos |
| `@LatencySLO` | TYPE, METHOD | gauss-augur | SLOs de latencia (p50/p95/p99) |
| `@Traced` | TYPE, METHOD | gauss-augur | Span OpenTelemetry en un método |
| `@Explainable` | METHOD | gauss-augur | Explicación SHAP de predicción |
| `@AnonymousAllowed` | METHOD | gauss-quarkus | Endpoint público (sin auth) |

---

## Dependencia Maven

```xml
<dependency>
  <groupId>io.gauss</groupId>
  <artifactId>gauss-core</artifactId>
</dependency>
```

---

## GraalVM native image

```bash
mvn package -Pnative
```

`NativeImageConfig` registra automáticamente todas las anotaciones del framework
para reflexión, garantizando compatibilidad con la compilación nativa de Quarkus.

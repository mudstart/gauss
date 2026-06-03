---
layout: default
title: Seguridad y observabilidad
parent: Guías
nav_order: 2
---

# Seguridad y observabilidad
{: .no_toc }

<details open markdown="block">
<summary>Contenido</summary>
{: .text-delta }
1. TOC
{:toc}
</details>

---

## Seguridad por defecto

Todos los endpoints `@MLEndpoint` requieren autenticación JWT por defecto.
Sólo los marcados con `@AnonymousAllowed` son públicos.

```java
@MLEndpoint
public class ChurnEndpoint {
    @AnonymousAllowed
    public String health() { return "ok"; }

    @RolesAllowed({"ML_ENGINEER"})
    public double predict(Input input) { ... }
}
```

### Roles estándar

| Rol | Permisos |
|-----|----------|
| `ML_ENGINEER` | Promover modelos, acceder a feature store, ejecutar pipelines |
| `DATA_SCIENTIST` | Registrar experimentos, leer catálogo de features |
| `ADMIN` | Panel de administración, ver todos los namespaces |

---

## Métricas con Micrometer

Las métricas se registran automáticamente en cada predicción:

| Métrica | Tags | Descripción |
|---------|------|-------------|
| `dsml.prediction.latency` | `endpoint`, `status` | Timer por llamada |
| `dsml.prediction.count` | `endpoint`, `result` | Contador total |
| `dsml.llm.tokens` | `provider`, `model`, `endpoint` | Tokens LLM |
| `dsml.feature.cache.hit` | `feature` | Hit ratio del feature store |

Disponibles en `/q/metrics` (formato Prometheus). Dashboard Grafana incluido en `docs/grafana-dashboard.json`.

---

## Trazas OpenTelemetry

```java
// OTLP exporter (Jaeger, Tempo, Zipkin…)
GaussTracer tracer = new GaussTracer(otlpExporter);

try (var span = tracer.startSpan("predict", "SERVER")) {
    span.setAttribute("endpoint", "churn")
        .setAttribute("customer_id", customerId);
    double score = predict(input);
}
```

Cada `@Transform` en un pipeline genera automáticamente un span hijo,
permitiendo identificar cuellos de botella end-to-end.

---

## CI/CD con templates incluidos

Los proyectos Gauss incluyen templates listos para usar:

### GitHub Actions (`.github/workflows/ci.yml`)

```yaml
jobs:
  build:
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin', cache: maven }
      - run: mvn --no-transfer-progress verify
      - run: mvn --no-transfer-progress gauss:verify-ts-contracts
```

### GitLab CI (`.gitlab-ci.yml`)

```yaml
test:
  script: ./mvnw --no-transfer-progress test
verify-ts-contracts:
  script: ./mvnw --no-transfer-progress gauss:verify-ts-contracts
```

---

## SLOs de latencia en CI

```java
@LatencySLO(p50 = "5ms", p95 = "20ms", p99 = "50ms")
public class ChurnEndpoint { ... }
```

```bash
mvn gauss:benchmark -Pbenchmark   # ejecuta JMH y verifica los SLOs
```

Si algún percentil supera el target, el build falla con:
```
[CHURN] SLO FAIL — p50=3ms p95=22ms p99=68ms
  [ChurnEndpoint] p95 SLO violated: target=20ms actual=22ms
  [ChurnEndpoint] p99 SLO violated: target=50ms actual=68ms
```

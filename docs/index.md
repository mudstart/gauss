---
layout: home
title: Inicio
nav_order: 1
description: "Gauss — Framework JVM de producción para Data Science y Machine Learning"
permalink: /
---

<div class="gauss-hero">
  <h1>Gauss Framework</h1>
  <p>
    Framework JVM de producción para <strong>Data Science y Machine Learning</strong>.<br>
    Anotaciones Java que generan automáticamente endpoints HTTP, clientes TypeScript,
    pipelines ETL, feature store y experiment tracking &mdash; sin boilerplate.
  </p>
  <p>
    <code>@MLEndpoint</code> &nbsp;·&nbsp;
    <code>@DataPipeline</code> &nbsp;·&nbsp;
    <code>@Feature</code> &nbsp;·&nbsp;
    <code>@Experiment</code>
  </p>
</div>

<div class="stat-strip">
  <div class="stat"><div class="num">13</div><div class="lbl">Módulos Maven</div></div>
  <div class="stat"><div class="num">59</div><div class="lbl">HUs completadas</div></div>
  <div class="stat"><div class="num">522</div><div class="lbl">Story Points</div></div>
  <div class="stat"><div class="num">~942</div><div class="lbl">Tests en verde</div></div>
  <div class="stat"><div class="num">Java 21</div><div class="lbl">+ Quarkus 3.15</div></div>
</div>

## ¿Qué problema resuelve?

Construir un sistema ML en producción requiere conectar decenas de piezas:
endpoints REST, serialización, seguridad, clientes frontend, pipelines ETL,
caché de features, experiment tracking, model registry, audit log, GDPR…

**Gauss lo hace con anotaciones:**

```java
@MLEndpoint                          // → endpoint HTTP + cliente TypeScript
@LatencySLO(p99 = "50ms")            // → SLO verificado en CI
@DriftMonitor(threshold = 0.1)       // → alerta si los datos cambian
@CircuitBreaker(threshold = 5)        // → fallback automático
public class ChurnEndpoint {

    @CachedPrediction(ttl = "5m")
    @Explainable(topFeatures = 5)    // → valores SHAP en la respuesta
    public double predict(CustomerInput input) {
        return model.predict(toTensor(input));
    }
}
```

---

## Módulos

<div class="module-grid">

  <div class="module-card">
    <div class="badge">Core</div>
    <h3><a href="{{ site.baseurl }}/modules/core">gauss-core</a></h3>
    <p>21 anotaciones fundamentales y configuración GraalVM native image.</p>
  </div>

  <div class="module-card">
    <div class="badge">Frontend</div>
    <h3><a href="{{ site.baseurl }}/modules/vela">gauss-vela</a></h3>
    <p>Genera interfaces TypeScript, esquemas Zod y clientes async desde POJOs Java.</p>
  </div>

  <div class="module-card">
    <div class="badge">Pipelines</div>
    <h3><a href="{{ site.baseurl }}/modules/flume">gauss-flume</a></h3>
    <p>Pipelines ETL declarativos con <code>@DataPipeline</code>, cron scheduler y fuentes JDBC/CSV/HTTP.</p>
  </div>

  <div class="module-card">
    <div class="badge">Serving</div>
    <h3><a href="{{ site.baseurl }}/modules/augur">gauss-augur</a></h3>
    <p>Inferencia ONNX, LLMs (LangChain4j), caché, circuit breaker, SHAP, drift, SLOs.</p>
  </div>

  <div class="module-card">
    <div class="badge">Experiments</div>
    <h3><a href="{{ site.baseurl }}/modules/vigil">gauss-vigil</a></h3>
    <p>Experiment tracking, Model Registry, guardrails, rollback automático, A/B estadístico.</p>
  </div>

  <div class="module-card">
    <div class="badge">Features</div>
    <h3><a href="{{ site.baseurl }}/modules/stratum">gauss-stratum</a></h3>
    <p>Feature store online (&lt;10ms) y offline, grafo de dependencias y catálogo.</p>
  </div>

  <div class="module-card">
    <div class="badge">Runtime</div>
    <h3><a href="{{ site.baseurl }}/modules/quarkus">gauss-quarkus</a></h3>
    <p>Adaptador CDI para Quarkus, seguridad JWT y OAuth2/OIDC.</p>
  </div>

  <div class="module-card">
    <div class="badge">Governance</div>
    <h3><a href="{{ site.baseurl }}/modules/lex">gauss-lex</a></h3>
    <p>Audit log inmutable, linaje end-to-end, GDPR, secretos y multi-tenancy.</p>
  </div>

  <div class="module-card">
    <div class="badge">Distributed</div>
    <h3><a href="{{ site.baseurl }}/modules/spark">gauss-spark</a></h3>
    <p>Ejecuta <code>@DataPipeline</code> en Apache Spark para datasets &gt; memoria JVM.</p>
  </div>

</div>

---

## Inicio rápido

```bash
# 1. Instalar Gauss localmente
git clone https://github.com/gauss-framework/gauss.git
cd gauss && mvn install -DskipTests

# 2. Crear un proyecto nuevo
gauss new mi-modelo --group-id=com.empresa --runtime=quarkus

# 3. Arrancar en modo desarrollo
cd mi-modelo
mvn quarkus:dev          # backend con live reload
cd frontend && npm run dev  # frontend con HMR
```

Visita `http://localhost:8080` para ver el endpoint de ejemplo y la Dev UI de Quarkus.

---

## Cumplimiento regulatorio

Gauss está diseñado para entornos regulados:

| Normativa | Capacidad |
|-----------|-----------|
| **GDPR** | `ComplianceService.deleteSubject()` + `DeletionCertificate` |
| **EU AI Act** | Linaje end-to-end (`LineageService`), Model Card (`@ModelCard`) |
| **ISO 27001** | Audit log inmutable en CEF/JSON (`AuditLog`) |

---

[Guía de instalación →]({{ site.baseurl }}/getting-started){: .btn .btn-primary .fs-5 .mb-4 .mb-md-0 }
[Referencia de anotaciones →]({{ site.baseurl }}/reference/annotations){: .btn .fs-5 .mb-4 .mb-md-0 }

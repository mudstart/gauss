---
layout: default
title: gauss-flume
parent: Módulos
nav_order: 3
---

# gauss-flume — Pipelines de datos
{: .no_toc }

<details open markdown="block">
<summary>Contenido</summary>
{: .text-delta }
1. TOC
{:toc}
</details>

Sistema declarativo para definir y ejecutar pipelines ETL con anotaciones Java.

---

## Definir un pipeline

```java
@DataPipeline("churn-features")
@Scheduled(cron = "0 2 * * *", description = "Reentrenamiento diario 2AM")
public class ChurnFeaturePipeline {

    @Ingest(source = "jdbc://datasource/customers")
    public Dataset<Customer> loadCustomers() { ... }

    @Ingest(source = "file://data/transactions.csv")
    public Dataset<Transaction> loadTransactions() { ... }

    @Transform
    public Dataset<ChurnFeature> engineer(Dataset<Customer> customers,
                                           Dataset<Transaction> transactions) {
        return customers.join(transactions).aggregate(ChurnFeatureAggregator::compute);
    }
}
```

---

## Fuentes de datos

| Protocolo `source` | Descripción |
|-------------------|-------------|
| `jdbc://datasource/tabla` | Base de datos JDBC |
| `file://ruta.csv` | CSV |
| `file://ruta.json` | JSON (array o por línea) |
| `file://ruta.parquet` | Parquet |
| `http://api-url` | API REST (GET + deserialización JSON) |
| `memory://nombre` | En memoria (tests con `@MockSource`) |

---

## Ejecución programática

```java
PipelineRunner runner = new PipelineRunner();
runner.run("churn-features", new ChurnFeaturePipeline());
```

---

## Programación con cron

```
┌─── minuto    (0-59)
│  ┌─── hora      (0-23)
│  │  ┌─── día mes (1-31)
│  │  │  ┌─── mes    (1-12)
│  │  │  │  ┌─── semana (0-7, 0=7=dom)
*  *  *  *  *
```

| Expresión | Significado |
|-----------|-------------|
| `0 2 * * *` | Diariamente a las 2:00 |
| `*/15 * * * *` | Cada 15 minutos |
| `0 9-17 * * 1-5` | Cada hora de 9 a 17, L-V |

---

## Monitorización del estado

```java
String execId = statusSvc.recordStart("churn-features", Instant.now());
try {
    runner.run("churn-features", pipeline);
    statusSvc.recordSuccess(execId, Instant.now());
} catch (Exception e) {
    statusSvc.recordFailure(execId, Instant.now(), e.getMessage());
}

// Consultar las últimas 20 ejecuciones
List<PipelineExecutionSummary> recent = statusSvc.recent(20);
```

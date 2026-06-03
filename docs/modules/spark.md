---
layout: default
title: gauss-spark
parent: Módulos
nav_order: 9
---

# gauss-spark — Ejecución distribuida
{: .no_toc }

Módulo **opcional** que ejecuta pipelines `@DataPipeline` en un cluster Apache Spark
para datasets que no caben en memoria JVM.

{: .note }
Spark no es una dependencia de compilación de `gauss-spark`. Se detecta en
*runtime* via reflexión, lo que significa que importar `gauss-spark` no fuerza
a ningún proyecto que no lo necesite a descargar Spark (~300 MB).

---

## Configuración

```java
@DataPipeline("big-etl")
@SparkExecution(
    master         = "spark://cluster:7077",
    appName        = "gauss-big-etl",
    executorMemory = "4g",
    executorCores  = 4
)
public class BigDataPipeline {

    @Ingest(source = "jdbc://warehouse/events")
    public Dataset<Event> load() { ... }

    @Transform
    public Dataset<Feature> compute(Dataset<Event> events) { ... }
}
```

---

## Ejecución

```java
SparkPipelineRunner runner = new SparkPipelineRunner();
SparkJobResult result = runner.run(new BigDataPipeline());

System.out.printf("Ejecutado %s: %d registros en %d ms%n",
    result.executedLocally() ? "localmente" : "en Spark",
    result.recordsWritten(),
    result.duration().toMillis());
```

Si Spark **no está** en el classpath, el runner cae automáticamente a ejecución local con un aviso.

---

## Modos de master

| Master | Uso |
|--------|-----|
| `local[*]` | Local, todos los núcleos (por defecto y para tests) |
| `local[4]` | Local, 4 hilos |
| `spark://host:7077` | Cluster standalone |
| `yarn` | Apache YARN |
| `k8s://https://api:6443` | Kubernetes |

---

## Añadir Spark al proyecto destino

Para usar un cluster real, añade Spark como dependencia `provided` en el
proyecto que ejecuta el pipeline y despliega con `spark-submit`:

```xml
<dependency>
  <groupId>org.apache.spark</groupId>
  <artifactId>spark-sql_2.13</artifactId>
  <version>3.5.1</version>
  <scope>provided</scope>
</dependency>
```

```bash
spark-submit \
  --class com.empresa.MainJob \
  --master spark://cluster:7077 \
  mi-modelo-fat.jar
```

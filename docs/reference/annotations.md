---
layout: default
title: Anotaciones
parent: Referencia
nav_order: 1
---

# Referencia de anotaciones
{: .no_toc }

<details open markdown="block">
<summary>Contenido</summary>
{: .text-delta }
1. TOC
{:toc}
</details>

---

## `@MLEndpoint`

```java
@Target(TYPE) @Retention(RUNTIME)
public @interface MLEndpoint { }
```

Expone la clase como endpoint HTTP ML. El path se deriva por convención (`camelToKebab`).
Genera automáticamente el cliente TypeScript via `gauss-vela`.

---

## `@DataPipeline`

```java
@Target(TYPE) @Retention(RUNTIME)
public @interface DataPipeline {
    String value();           // nombre único del pipeline
    String description() default "";
}
```

---

## `@Ingest`

```java
@Target(METHOD) @Retention(RUNTIME)
public @interface Ingest {
    String source();          // jdbc://, file://, http://, memory://
}
```

---

## `@Transform`

```java
@Target(METHOD) @Retention(RUNTIME)
public @interface Transform { }
```

El orden de ejecución se infiere del grafo de dependencias entre parámetros y tipos de retorno.

---

## `@Scheduled`

```java
@Target({TYPE, METHOD}) @Retention(RUNTIME)
public @interface Scheduled {
    String cron();                // 5 campos: min hora dia mes semana
    String description() default "";
}
```

---

## `@SparkExecution`

```java
@Target(TYPE) @Retention(RUNTIME)
public @interface SparkExecution {
    String master()               default "local[*]";
    String appName()              default "";
    String executorMemory()       default "1g";
    int    executorCores()        default 2;
    boolean adaptiveQueryExecution() default true;
}
```

---

## `@Feature`

```java
@Target(METHOD) @Retention(RUNTIME)
public @interface Feature {
    String ttl();                 // "1h", "30m", "7d", "60s"
    String description()  default "";
    int    version()      default 1;
}
```

---

## `@Experiment`

```java
@Target(METHOD) @Retention(RUNTIME)
public @interface Experiment {
    String   name()   default "";
    String[] tags()   default {};
}
```

---

## `@ModelCard`

```java
@Target(TYPE) @Retention(RUNTIME)
public @interface ModelCard {
    String description()  default "";
    String intendedUse()  default "";
    String limitations()  default "";
    String trainedOn()    default "";
    String version()      default "1.0";
}
```

---

## `@ModelVersion`

```java
@Target(TYPE) @Retention(RUNTIME)
@Repeatable(ModelVersions.class)
public @interface ModelVersion {
    String value();               // identificador de versión
    int    weight();              // porcentaje de tráfico (0-100)
    int    minSampleSize() default 100;
}
```

---

## `@ModelGuardrail`

```java
@Target({METHOD, TYPE}) @Retention(RUNTIME)
@Repeatable(ModelGuardrails.class)
public @interface ModelGuardrail {
    String metric();
    double min()  default Double.NEGATIVE_INFINITY;
    double max()  default Double.POSITIVE_INFINITY;
}
```

---

## `@CachedPrediction`

```java
@Target(METHOD) @Retention(RUNTIME)
public @interface CachedPrediction {
    String ttl()     default "5m";   // "5m", "1h", "30s", "1d"
    String backend() default "memory"; // "memory" | "redis"
}
```

---

## `@CacheEvict`

```java
@Target(METHOD) @Retention(RUNTIME)
public @interface CacheEvict {
    String cacheName() default "";   // vacío = endpoint actual
}
```

---

## `@CircuitBreaker`

```java
@Target(METHOD) @Retention(RUNTIME)
public @interface CircuitBreaker {
    int    threshold() default 5;    // fallos consecutivos antes de abrir
    String delay()     default "30s";// tiempo en OPEN antes de HALF_OPEN
    String fallback()  default "";   // nombre del método de fallback
}
```

---

## `@AutoRollback`

```java
@Target(TYPE) @Retention(RUNTIME)
public @interface AutoRollback {
    String metric();
    double threshold();
    int    windowMinutes() default 10;
    int    maxPerHour()    default 3;
}
```

---

## `@DriftMonitor`

```java
@Target(TYPE) @Retention(RUNTIME)
public @interface DriftMonitor {
    String metric()     default "PSI";
    double threshold()  default 0.1;
    int    sampleSize() default 100;
}
```

---

## `@LatencySLO`

```java
@Target({TYPE, METHOD}) @Retention(RUNTIME)
public @interface LatencySLO {
    String p50() default "";   // "5ms", "1s", "2m"
    String p95() default "";
    String p99() default "";
}
```

---

## `@Traced`

```java
@Target({METHOD, TYPE}) @Retention(RUNTIME)
public @interface Traced {
    String operationName() default "";
    String kind()          default "INTERNAL";
}
```

---

## `@Explainable`

```java
@Target(METHOD) @Retention(RUNTIME)
public @interface Explainable {
    int     topFeatures() default 5;
    boolean async()       default true;
}
```

---

## `@BatchPrediction`

```java
@Target(METHOD) @Retention(RUNTIME)
public @interface BatchPrediction {
    int batchSize() default 100;
}
```

---

## `@AnonymousAllowed`

```java
@Target(METHOD) @Retention(RUNTIME)
public @interface AnonymousAllowed { }
```

---
layout: default
title: gauss-stratum
parent: Módulos
nav_order: 6
---

# gauss-stratum — Feature Store
{: .no_toc }

<details open markdown="block">
<summary>Contenido</summary>
{: .text-delta }
1. TOC
{:toc}
</details>

---

## Definir features

```java
public class CustomerFeatures {

    @Feature(ttl = "1h", description = "Transacciones en los últimos 30 días")
    public int txCount30d(String customerId) {
        return db.count("SELECT COUNT(*) FROM tx WHERE id=? AND date > NOW()-30d", customerId);
    }

    @Feature(ttl = "6h", description = "Customer Lifetime Value estimado")
    public double clv(String customerId) { return mlModel.predictClv(customerId); }

    // Feature con dependencia: se calcula después de txCount30d
    @Feature(ttl = "2h", description = "Conteo normalizado")
    public double normCount(String customerId, int txCount30d) {
        return txCount30d / referencePopulationMean;
    }
}
```

---

## Recuperación online (< 10 ms p99)

```java
OnlineFeatureStore store = new OnlineFeatureStore();

FeatureVector vector = store.getAll("customer-42",
    new CustomerFeatures(), CustomerFeatures.class);

double clv    = (double) vector.get("clv").orElseThrow();
int txCount   = (int)    vector.get("txCount30d").orElseThrow();

// Métricas de caché
long hits   = store.hitCount("clv");
long misses = store.missCount("clv");
```

El motor usa **Caffeine** como caché de baja latencia con TTL por entrada.

---

## Materialización offline para entrenamiento

```java
MaterializationResult result = offline.materialize(
    "2024-01-01", "2024-12-31",
    CustomerFeatures.class,
    customerIds
);

System.out.printf("Entidades: %d | Calculadas: %d | Saltadas: %d | Tiempo: %d ms%n",
    result.entitiesTotal(), result.featuresComputed(),
    result.featuresSkipped(), result.duration().toMillis());
```

La materialización es **incremental**: si un valor ya existe en el store (y no ha
expirado) se salta y se cuenta en `featuresSkipped()`.

---

## Catálogo de features

```java
FeatureCatalogService catalog = new FeatureCatalogService(
    CustomerFeatures.class, ProductFeatures.class);

catalog.listAll().forEach(e -> System.out.printf(
    "%-20s %-8s %s%n", e.name(), e.returnTypeName(), e.description()));

// Búsqueda
List<FeatureCatalogEntry> results = catalog.search("transaccion");
```

---

## Resolución de dependencias

El framework detecta automáticamente las dependencias entre features por coincidencia de tipo de retorno.
El orden de evaluación se garantiza mediante un **sort topológico** (DFS):

```
txCount30d → normCount
              ↑
           (depende de txCount30d)
```

Si hay ciclos, se lanza `IllegalStateException("Cyclic dependency detected")`.

# Gauss Framework — Manual de Referencia

> **Versión:** 1.0 · **Stack:** Java 21 · Quarkus 3.15 · Maven 3.9  
> **Módulos:** 13 · **Historias de usuario implementadas:** 59 / 59 · **Story Points:** 522 / 522

---

## Tabla de contenidos

1. [Introducción](#1-introducción)
2. [Instalación y primer proyecto](#2-instalación-y-primer-proyecto)
3. [gauss-core — Anotaciones fundamentales](#3-gauss-core--anotaciones-fundamentales)
4. [gauss-vela — Generación de TypeScript](#4-gauss-vela--generación-de-typescript)
5. [gauss-flume — Pipelines de datos](#5-gauss-flume--pipelines-de-datos)
6. [gauss-augur — Serving de modelos](#6-gauss-augur--serving-de-modelos)
7. [gauss-vigil — Experiment Tracking y Model Registry](#7-gauss-vigil--experiment-tracking-y-model-registry)
8. [gauss-stratum — Feature Store](#8-gauss-stratum--feature-store)
9. [gauss-quarkus — Adaptador Quarkus](#9-gauss-quarkus--adaptador-quarkus)
10. [gauss-lex — Gobernanza y cumplimiento](#10-gauss-lex--gobernanza-y-cumplimiento)
11. [gauss-spark — Ejecución distribuida](#11-gauss-spark--ejecución-distribuida)
12. [Seguridad y observabilidad](#12-seguridad-y-observabilidad)
13. [Testing](#13-testing)
14. [Referencia de anotaciones](#14-referencia-de-anotaciones)
15. [Referencia de propiedades](#15-referencia-de-propiedades)

---

## 1. Introducción

### 1.1 ¿Qué es Gauss?

Gauss es un framework JVM de producción para Data Science y Machine Learning. Su objetivo es eliminar el boilerplate de infraestructura de ML (endpoints HTTP, clientes TypeScript, pipelines ETL, feature store, experiment tracking) permitiendo al equipo de datos centrarse exclusivamente en la lógica de modelos.

```
Arquitectura de alto nivel
──────────────────────────────────────────────────────────
  [React / TypeScript]    ◄── gauss-vela genera el cliente
         │
  [Quarkus / Spring]      ◄── gauss-quarkus registra endpoints
         │
  ┌──────┴──────────────────────────────────────────┐
  │              GAUSS FRAMEWORK                    │
  │  gauss-augur    gauss-flume    gauss-stratum    │
  │  (serving)      (pipelines)    (feature store)  │
  │                                                 │
  │  gauss-vigil    gauss-lex      gauss-spark      │
  │  (experiments)  (governance)   (distributed)    │
  └─────────────────────────────────────────────────┘
         │
  [ONNX models / LLMs / JDBC / Files / Redis / S3]
──────────────────────────────────────────────────────────
```

### 1.2 Principios de diseño

| Principio | Descripción |
|-----------|-------------|
| **Anotaciones primero** | Toda la funcionalidad se activa con anotaciones Java estándar |
| **Zero-config** | Configuración razonable por defecto; sólo se configura lo que diverge |
| **Type-safe end-to-end** | Los DTOs Java generan interfaces TypeScript; los contratos se verifican en CI |
| **Testeable por diseño** | Cada módulo incluye test utilities (`GaussPipelineExtension`, `InMemoryFeatureStore`, …) |
| **Seguro por defecto** | Todos los endpoints requieren autenticación salvo `@AnonymousAllowed` |
| **Compliance-ready** | Audit log inmutable, linaje de datos, borrado GDPR, namespacing multi-tenant |

### 1.3 Stack tecnológico

- **Lenguaje:** Java 21 (records, pattern matching, text blocks)
- **Runtime principal:** Quarkus 3.15 con CDI (Arc)
- **Cache de predicciones:** Caffeine 3.x
- **Inferencia ONNX:** Microsoft ONNX Runtime 1.18
- **LLMs:** LangChain4j 0.36
- **Métricas:** Micrometer
- **Build:** Maven 3.9 con el plugin `gauss-maven-plugin`
- **Frontend generado:** React 18 + TypeScript + Vite + Zod

---

## 2. Instalación y primer proyecto

### 2.1 Requisitos

| Herramienta | Versión mínima |
|-------------|----------------|
| Java JDK | 21 |
| Maven | 3.9 |
| Node.js (para frontend) | 20 |
| Docker (opcional) | 24 |

### 2.2 Instalar Gauss en el repositorio local

```bash
git clone https://github.com/gauss-framework/gauss.git
cd gauss
mvn install -DskipTests
```

### 2.3 Crear un proyecto nuevo con la CLI

```bash
# Proyecto Quarkus (por defecto)
mvn io.gauss:gauss-maven-plugin:new \
    -Dname=mi-modelo \
    -DgroupId=com.empresa \
    -Druntime=quarkus

# Proyecto Spring Boot
mvn io.gauss:gauss-maven-plugin:new \
    -Dname=mi-modelo \
    -DgroupId=com.empresa \
    -Druntime=spring
```

O usar la CLI directamente:

```bash
gauss new mi-modelo --group-id=com.empresa --runtime=quarkus
```

### 2.4 Estructura del proyecto generado

```
mi-modelo/
├── pom.xml                          # POM con BOM de Gauss
├── README.md
├── .gitignore
├── .github/workflows/ci.yml         # GitHub Actions (HU-046)
├── .gitlab-ci.yml                   # GitLab CI (HU-046)
├── .gauss-ts-contracts              # Checksums TypeScript para CI
├── src/
│   └── main/
│       ├── java/com/empresa/mimodelo/
│       │   ├── GreetingEndpoint.java         # Ejemplo @MLEndpoint
│       │   ├── model/                        # DTOs
│       │   ├── pipeline/ChurnPipeline.java   # Ejemplo @DataPipeline
│       │   ├── features/CustomerFeatures.java# Ejemplo @Feature
│       │   └── training/ChurnExperiment.java # Ejemplo @Experiment
│       └── resources/application.properties
├── frontend/
│   ├── package.json
│   ├── vite.config.ts
│   ├── src/
│   │   ├── main.tsx
│   │   ├── App.tsx
│   │   └── generated/               # TypeScript generado por gauss-vela
│   └── index.html
├── pipelines/                       # YAMLs de configuración de pipelines
└── models/                          # Archivos ONNX
```

### 2.5 Inicio en modo desarrollo

```bash
# Backend (Quarkus con live reload)
mvn quarkus:dev

# Frontend (Vite con HMR)
cd frontend && npm install && npm run dev
```

### 2.6 Adopción modular (BOM)

Para añadir Gauss módulo a módulo en un proyecto existente:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.gauss</groupId>
      <artifactId>gauss-bom</artifactId>
      <version>0.1.0-SNAPSHOT</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<!-- Sólo los módulos necesarios -->
<dependencies>
  <dependency>
    <groupId>io.gauss</groupId>
    <artifactId>gauss-core</artifactId>
  </dependency>
  <dependency>
    <groupId>io.gauss</groupId>
    <artifactId>gauss-vigil</artifactId>
  </dependency>
</dependencies>
```

---

## 3. gauss-core — Anotaciones fundamentales

`gauss-core` es el único módulo sin dependencias externas. Define todas las anotaciones del framework y la configuración de GraalVM native image.

### 3.1 Anotaciones disponibles

| Anotación | Target | Módulo responsable |
|-----------|--------|--------------------|
| `@MLEndpoint` | TYPE | gauss-augur / gauss-quarkus |
| `@DataPipeline` | TYPE | gauss-flume |
| `@Ingest` | METHOD | gauss-flume |
| `@Transform` | METHOD | gauss-flume |
| `@Scheduled` | TYPE, METHOD | gauss-flume |
| `@SparkExecution` | TYPE | gauss-spark |
| `@Feature` | METHOD | gauss-stratum |
| `@Experiment` | METHOD | gauss-vigil |
| `@ModelCard` | TYPE | gauss-vigil |
| `@ModelVersion` | TYPE | gauss-augur |
| `@BatchPrediction` | METHOD | gauss-augur |
| `@CachedPrediction` | METHOD | gauss-augur |
| `@CacheEvict` | METHOD | gauss-augur |
| `@CircuitBreaker` | METHOD | gauss-augur |
| `@AutoRollback` | TYPE | gauss-vigil |
| `@DriftMonitor` | TYPE | gauss-augur |
| `@LatencySLO` | TYPE, METHOD | gauss-augur |
| `@Traced` | TYPE, METHOD | gauss-augur |
| `@Explainable` | METHOD | gauss-augur |
| `@ModelGuardrail` | METHOD, TYPE | gauss-vigil |
| `@AnonymousAllowed` | METHOD | gauss-quarkus |

### 3.2 GraalVM native image

Para compilar el proyecto a native image:

```bash
mvn package -Pnative
```

`gauss-core` registra automáticamente todas sus anotaciones y las clases de modelos para reflexión mediante `NativeImageConfig`.

---

## 4. gauss-vela — Generación de TypeScript

`gauss-vela` lee las clases Java anotadas con `@MLEndpoint` en tiempo de build y genera:
- Interfaces TypeScript desde POJOs Java
- Funciones cliente `async` para cada método del endpoint
- Esquemas Zod que replican las validaciones Jakarta Bean Validation
- Especificación OpenAPI 3.0

### 4.1 Generación automática en build

```xml
<!-- En el pom.xml del proyecto -->
<plugin>
  <groupId>io.gauss</groupId>
  <artifactId>gauss-maven-plugin</artifactId>
  <executions>
    <execution>
      <goals><goal>generate-ts</goal></goals>
    </execution>
  </executions>
  <configuration>
    <outputDirectory>${project.basedir}/frontend/generated</outputDirectory>
  </configuration>
</plugin>
```

```bash
# Generación manual
mvn gauss:generate-ts

# Verificar que los contratos TS no han cambiado (para CI)
mvn gauss:verify-ts-contracts

# Actualizar el archivo de checksums de referencia
mvn gauss:verify-ts-contracts -Dgauss.updateContracts=true
```

### 4.2 Mapeo de tipos Java → TypeScript

| Java | TypeScript |
|------|-----------|
| `int`, `long`, `double`, `float` | `number` |
| `String` | `string` |
| `boolean` | `boolean` |
| `List<T>` | `T[]` |
| `Map<K, V>` | `Record<K, V>` |
| `Optional<T>` | `T \| undefined` |
| `void` | `void` |
| `Multi<T>` (Mutiny) | `AsyncIterable<T>` |
| `Flux<T>` (Reactor) | `AsyncIterable<T>` |
| Enum Java | `enum` TypeScript |

### 4.3 Ejemplo completo

**Java:**
```java
@MLEndpoint
public class ChurnEndpoint {

    public record CustomerInput(
        @NotNull String customerId,
        @Min(0) int age,
        @Email String email
    ) {}

    public record ChurnResult(double probability, String reason) {}

    public ChurnResult predict(CustomerInput input) {
        return new ChurnResult(0.87, "high_transaction_drop");
    }
}
```

**TypeScript generado (`frontend/generated/ChurnEndpoint.ts`):**
```typescript
// AUTO-GENERATED by gauss-vela — do not edit manually

export interface CustomerInput {
  customerId: string;
  age: number;
  email: string;
}

export interface ChurnResult {
  probability: number;
  reason: string;
}

export const CustomerInputSchema = z.object({
  customerId: z.string().min(1),
  age: z.number().min(0),
  email: z.string().email(),
});

/** Predict churn probability for a customer. */
export async function predict(input: CustomerInput): Promise<ChurnResult> {
  const response = await fetch('/api/churn/predict', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(input),
  });
  if (!response.ok) throw new GaussError(response);
  return response.json();
}
```

### 4.4 Streaming reactivo

```java
@MLEndpoint
public class StreamingEndpoint {
    public Multi<String> generate(String prompt) {
        return Multi.createFrom().items("Hello", " ", "World");
    }
}
```

```typescript
// Uso en React:
for await (const token of generate({ prompt: "Hello" })) {
  setOutput(prev => prev + token);
}
```

---

## 5. gauss-flume — Pipelines de datos

`gauss-flume` proporciona un sistema declarativo para definir y ejecutar pipelines ETL con anotaciones Java.

### 5.1 Definición de un pipeline

```java
@DataPipeline("churn-features")
@Scheduled(cron = "0 2 * * *", description = "Reentrenamiento diario a las 2AM")
public class ChurnFeaturePipeline {

    @Ingest(source = "jdbc://datasource/customers")
    public Dataset<Customer> loadCustomers() {
        return jdbcTemplate.query("SELECT * FROM customers", customerMapper);
    }

    @Ingest(source = "file://data/transactions.csv")
    public Dataset<Transaction> loadTransactions() {
        return CsvReader.read("data/transactions.csv", Transaction.class);
    }

    @Transform
    public Dataset<ChurnFeature> engineer(Dataset<Customer> customers,
                                           Dataset<Transaction> transactions) {
        return customers.join(transactions)
                        .aggregate(ChurnFeatureAggregator::compute);
    }
}
```

### 5.2 Fuentes de datos soportadas

| Protocolo `source` | Descripción |
|-------------------|-------------|
| `jdbc://datasource/tabla` | Base de datos JDBC |
| `file://ruta.csv` | CSV (delimitado por comas) |
| `file://ruta.json` | JSON (array o objeto por línea) |
| `file://ruta.parquet` | Parquet |
| `http://api-url` | API REST (GET, deserialización automática) |
| `memory://nombre` | Datos en memoria (para tests con `@MockSource`) |

### 5.3 Ejecución de pipelines

```java
// Ejecución programática
PipelineRunner runner = new PipelineRunner();
runner.run("churn-features", new ChurnFeaturePipeline());

// Con fuentes simuladas (en tests)
PipelineTestRunner.withMockSource("customers", List.of(customer1, customer2))
                  .withMockSource("transactions", List.of(tx1, tx2, tx3))
                  .run("churn-features", new ChurnFeaturePipeline());
```

### 5.4 Pipelines programados con cron

```java
@DataPipeline("weekly-report")
@Scheduled(cron = "0 6 * * 1", description = "Lunes a las 6AM")
public class WeeklyReportPipeline { ... }
```

**Sintaxis cron (5 campos):**

```
┌─── minuto (0-59)
│  ┌─── hora (0-23)
│  │  ┌─── día del mes (1-31)
│  │  │  ┌─── mes (1-12)
│  │  │  │  ┌─── día de la semana (0-7, 0=7=domingo)
│  │  │  │  │
*  *  *  *  *
```

| Expresión | Significado |
|-----------|-------------|
| `0 2 * * *` | Diariamente a las 2:00 |
| `*/15 * * * *` | Cada 15 minutos |
| `0 9-17 * * 1-5` | Cada hora de 9 a 17, lunes a viernes |
| `0 0 1 * *` | Primer día de cada mes a medianoche |

### 5.5 Monitorización del estado del pipeline

```java
// Registro de ejecuciones
PipelineStatusService statusSvc = new PipelineStatusService();
String execId = statusSvc.recordStart("churn-features", Instant.now());

try {
    runner.run("churn-features", pipeline);
    statusSvc.recordSuccess(execId, Instant.now());
} catch (Exception e) {
    statusSvc.recordFailure(execId, Instant.now(), e.getMessage());
}

// Consulta de estado
List<PipelineExecutionSummary> recent = statusSvc.recent(20);
```

---

## 6. gauss-augur — Serving de modelos

`gauss-augur` proporciona todas las capacidades de serving: endpoints HTTP, inferencia ONNX, integración con LLMs, caché de predicciones, circuit breaker, versionado A/B, batch prediction, explicabilidad SHAP y detección de drift.

### 6.1 Exponer un modelo con `@MLEndpoint`

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
        float[] tensor = toTensor(input);
        return model.predict(tensor)[0];
    }

    public double safePredict(CustomerInput input) {
        return 0.5;  // valor seguro por defecto
    }
}
```

El path HTTP se deriva automáticamente: `ChurnEndpoint` → `/api/churn`.

### 6.2 Carga e inferencia ONNX

```java
// Carga automática al arrancar
@InjectModel("models/churn.onnx")
private OnnxModel model;

// Predicción con tipos Java
float[] input   = new float[]{age, income, tenure};
float[] output  = model.predict(input);
double  score   = output[0];
```

El modelo se cachea en memoria en el arranque. Se soportan modelos de **clasificación**, **regresión** y **transformers** (texto).

### 6.3 Integración con LLMs vía LangChain4j

```java
@MLEndpoint
public class ChatbotEndpoint {

    @LLMEndpoint(provider = "openai", model = "gpt-4o")
    public String chat(String userMessage) {
        // LangChain4j gestiona el historial de conversación
        return aiService.generate(userMessage);
    }

    @LLMEndpoint(provider = "ollama")  // Modelo local
    public Multi<String> streamChat(String message) {
        return aiService.streamGenerate(message);
    }
}
```

### 6.4 Guardrails para inputs de LLMs

```java
PromptGuardrailService guardrails = new PromptGuardrailService()
    // SPI: añadir patrones personalizados
    .withPattern(new GuardrailPattern("no-pii", "\\d{3}-\\d{2}-\\d{4}", "Bloquear SSNs"));

// En el interceptor del endpoint:
guardrails.validate(userInput);  // lanza LLMGuardrailViolationException si bloqueado
```

**Patrones built-in:**
- `ignore_instructions` — "Ignora las instrucciones anteriores"
- `system_override` — Intentos de sobreescribir el system prompt
- `jailbreak_roleplay` — DAN y variantes
- `delimiter_injection` — Delimitadores ocultos (```` ``` ````, `<|im_start|>`, …)
- `prompt_exfiltration` — "Repite tu system prompt"

### 6.5 Versionado A/B de modelos

```java
@MLEndpoint
@ModelVersion(value = "v1", weight = 80)
@ModelVersion(value = "v2", weight = 20)
public class ChurnEndpoint {
    // El framework enruta el tráfico automáticamente
}
```

```java
// Actualizar pesos en caliente (sin reiniciar)
VersionRouter router = VersionRouter.fromAnnotations(ChurnEndpoint.class);
router.updateWeights(List.of(
    new VersionWeight("v1", 50),
    new VersionWeight("v2", 50)
));

// Monitorizar distribución real
long v1Calls = router.callCount("v1");
long v2Calls = router.callCount("v2");
```

### 6.6 Batch prediction asíncrono

```java
BatchJobTracker tracker = new BatchJobTracker();

// Lanzar job
String jobId = tracker.submit(
    "churn",
    customerIds,
    id -> model.predict(toTensor(id)),
    batchSize: 100
);

// Consultar progreso
BatchJob job = tracker.findJob(jobId).orElseThrow();
System.out.println(job.progressPercent() + "% completado");

// Cancelar
tracker.cancel(jobId);
```

### 6.7 Caché de predicciones

```java
@CachedPrediction(ttl = "5m", backend = "memory")
public double predict(CustomerInput input) { ... }

@CacheEvict
public void refreshModel() { ... }  // invalida la caché
```

La clave de caché es `(endpointName, hash(input))`. Se soportan backends `"memory"` (Caffeine) y `"redis"`.

### 6.8 Circuit breaker

```java
@CircuitBreaker(threshold = 5, delay = "30s", fallback = "safePredict")
public double predict(CustomerInput input) { ... }
```

**Estados del circuito:**
- `CLOSED` → operación normal
- `OPEN` → circuito abierto tras `threshold` fallos consecutivos; llama al fallback
- `HALF_OPEN` → tras `delay`, permite una llamada de prueba

### 6.9 Tracking de tokens LLM y control de costes

```java
TokenUsageTracker tracker = new TokenUsageTracker();

// Registrar uso
tracker.record(TokenUsage.of(500, 200, "openai", "gpt-4o", "chatbot"));

// Métricas agregadas
long    totalInput  = tracker.totalInputTokens("chatbot");
double  totalCost   = tracker.totalCostUsd("chatbot");

// Alerta de presupuesto (80% de $10/mes)
boolean alerta = tracker.isBudgetAlertTriggered("chatbot", 10.0, 0.80);
```

**Precios configurados:** OpenAI (GPT-4o, GPT-4o-mini, GPT-3.5), Anthropic (Claude 3), Google (Gemini 1.5). Ollama y otros proveedores locales: coste = $0.

### 6.10 Detección de drift (PSI)

```java
DriftMonitorService drift = new DriftMonitorService();
drift.setReference("churn", trainingData, numBuckets: 10);

// Tras cada predicción:
drift.recordObservation("churn", inputFeatureValue);

// Evaluar drift (llamar periódicamente)
drift.evaluate("churn", threshold: 0.1, numBuckets: 10)
     .ifPresent(report -> {
         if (report.alert()) log.warn(report.summary());
     });
```

**Interpretación PSI:**
- `< 0.10` → distribución estable
- `0.10 – 0.25` → cambio moderado, investigar
- `> 0.25` → cambio mayor, reentrenar el modelo

### 6.11 Explicabilidad SHAP

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

Para un modelo lineal `f(x) = Σ cᵢ·xᵢ`, el valor SHAP de la feature `i` es exactamente `cᵢ·(xᵢ − background_i)`. La propiedad de eficiencia garantiza que `Σ SHAP_i = f(x) − E[f(x)]`.

### 6.12 SLOs de latencia

```java
@MLEndpoint
@LatencySLO(p50 = "5ms", p95 = "20ms", p99 = "50ms")
public class ChurnEndpoint { ... }
```

```bash
# Verificar SLOs en CI
mvn gauss:benchmark
```

---

## 7. gauss-vigil — Experiment Tracking y Model Registry

### 7.1 Registrar un experimento

```java
ExperimentRunner runner = new ExperimentRunner(experimentStore);

double auc = runner.run(
    "churn-xgboost",
    new String[]{"xgboost", "churn"},
    Map.of("learningRate", 0.05, "maxDepth", 6),
    ctx -> {
        XGBoostModel model = XGBoost.train(dataset, 0.05, 6);
        ctx.logMetric("auc",  model.getAuc());
        ctx.logMetric("f1",   model.getF1());
        ctx.logArtifact("confusion_matrix", model.confusionMatrix());
        return model.getAuc();
    }
);
```

O con la anotación (para interceptores CDI):

```java
@Experiment(name = "churn-xgboost", tags = {"xgboost", "churn"})
public TrainedModel train(double learningRate, int maxDepth, ExperimentContext ctx) {
    TrainedModel model = XGBoost.train(dataset, learningRate, maxDepth);
    ctx.logMetric("auc", model.getAuc());
    return model;
}
```

### 7.2 Consultar experimentos (dashboard API)

```java
ExperimentQueryService queryService = new ExperimentQueryService(store);

List<ExperimentRun> runs = queryService.query(
    ExperimentQuery.builder()
        .name("churn-xgboost")
        .tags(List.of("xgboost"))
        .fromDate(Instant.now().minus(Duration.ofDays(7)))
        .sortByMetric("auc")
        .pageSize(20)
        .build()
);

// Comparar dos experimentos
ExperimentDiff diff = queryService.diff("run-id-1", "run-id-2");
diff.metricsDiff();  // double[] con las diferencias de cada métrica
diff.paramsDiff();   // Object[] con las diferencias de cada parámetro
```

### 7.3 Streaming de métricas en tiempo real

```java
MetricStreamService stream = new MetricStreamService();

// Durante el entrenamiento (cada epoch):
for (int epoch = 0; epoch < 100; epoch++) {
    double loss = trainEpoch(epoch);
    stream.log(experimentId, "loss", loss, epoch);
    stream.log(experimentId, "auc",  computeAuc(), epoch);
}

// Endpoint SSE que hace polling:
List<StepMetric> nuevos = stream.since(experimentId, "loss", lastStep);
```

### 7.4 Model Registry

```java
// Registrar un modelo desde un experimento
String modelId = ModelRegistry.register(
    "churn-v2",         // nombre
    experimentRunId,    // vincula al experimento
    "models/churn-v2.onnx"
);

// Promover a staging → production
ModelRegistry.promote(modelId, Stage.PRODUCTION, "alice");

// Con guardrails (bloquea si AUC < 0.90)
ModelRegistry.promote(modelId, Stage.PRODUCTION, "alice",
    new ModelGuardrail[]{
        @ModelGuardrail(metric = "auc", min = 0.90)
    });

// Consultar modelos en producción
List<ModelRegistration> production =
    ModelRegistry.findByStage(Stage.PRODUCTION);
```

### 7.5 Rollback automático

```java
@AutoRollback(metric = "error_rate", threshold = 0.15, windowMinutes = 10)
@MLEndpoint
public class ChurnEndpoint { ... }
```

```java
RollbackService rollback = new RollbackService();
rollback.recordMetric(modelId, "error_rate", observedErrorRate);

rollback.evaluate(modelId, RollbackPolicy.of("error_rate", 0.15))
        .ifPresent(event -> {
            alertManager.send(event.summary());
            auditLog.append(
                AuditEvent.builder(AuditAction.MODEL_ROLLED_BACK)
                    .actor("system")
                    .resource("model:" + event.modelName())
                    .build()
            );
        });
```

### 7.6 Análisis estadístico A/B

```java
StatisticalTestService stats = new StatisticalTestService();

// Para métricas binarias (éxito/fallo)
ABTestResult result = stats.testProportions("v1", "v2", samples, 0.05);

// Para métricas continuas (latencia, RMSE, AUC)
ABTestResult result = stats.testMeans("v1", "v2", samples, 0.05);

if (result.significant()) {
    System.out.println("Ganador: " + result.recommendedWinner().orElseThrow());
    System.out.printf("p-value: %.4f%n", result.pValue());
    System.out.printf("IC 95%% v1: [%.3f, %.3f]%n",
        result.ci95LowerA(), result.ci95UpperA());
}
```

### 7.7 Model Card

```java
@ModelCard(
    description   = "XGBoost churn prediction model",
    intendedUse   = "Predict B2C customer churn in next 30 days",
    limitations   = "Not suitable for B2B segments",
    trainedOn     = "Customer transactions 2020-2024",
    version       = "2.0"
)
@DataPipeline("churn-training")
public class ChurnTrainingPipeline { ... }
```

```java
ModelCardService cardService = new ModelCardService(experimentStore);
ModelCardEntry card = cardService.build(registration, ChurnTrainingPipeline.class);
String json = cardService.toJson(card);  // JSON compatible con Hugging Face Model Cards
```

### 7.8 Health checks

```java
// Implementar un indicador personalizado
public class FeatureStoreHealthIndicator implements HealthIndicator {
    @Override
    public ComponentHealth check() {
        try {
            featureStore.ping();
            return ComponentHealth.up("feature-store");
        } catch (Exception e) {
            return ComponentHealth.down("feature-store", e.getMessage());
        }
    }
}

// El servicio agrega todos los indicadores
GaussHealthService healthService = new GaussHealthService(ServiceLoader.load(...));
GaussHealthReport report = healthService.health();
// Resultado: UP | DEGRADED | DOWN
```

---

## 8. gauss-stratum — Feature Store

### 8.1 Definir features

```java
public class CustomerFeatures {

    @Feature(ttl = "1h", description = "Número de transacciones en los últimos 30 días")
    public int txCount30d(String customerId) {
        return db.count("SELECT COUNT(*) FROM tx WHERE customer_id=? AND date > NOW()-30d",
                        customerId);
    }

    @Feature(ttl = "6h", description = "Customer Lifetime Value estimado")
    public double clv(String customerId) {
        return mlModel.predictClv(customerId);
    }

    // Feature con dependencia: usa txCount30d como parámetro
    @Feature(ttl = "2h", description = "Normalised transaction count")
    public double normCount(String customerId, int txCount30d) {
        return txCount30d / referencePopulationMean;
    }
}
```

### 8.2 Recuperación online (< 10 ms)

```java
OnlineFeatureStore store = new OnlineFeatureStore();

// Obtener todas las features de un cliente (respuesta en < 10ms p99)
FeatureVector vector = store.getAll("customer-42", new CustomerFeatures(), CustomerFeatures.class);

double clv         = (double) vector.get("clv").orElseThrow();
int    txCount     = (int)    vector.get("txCount30d").orElseThrow();

// Métricas de caché
long hits   = store.hitCount("clv");
long misses = store.missCount("clv");
```

### 8.3 Materialización offline para entrenamiento

```java
OfflineFeatureStore offline = new OfflineFeatureStore(targetStore, new CustomerFeatures());

MaterializationResult result = offline.materialize(
    "2024-01-01",
    "2024-12-31",
    CustomerFeatures.class,
    customerIds  // List<String>
);

System.out.printf("Entidades: %d | Calculadas: %d | Saltadas (caché): %d | Tiempo: %d ms%n",
    result.entitiesTotal(),
    result.featuresComputed(),
    result.featuresSkipped(),
    result.duration().toMillis()
);
```

### 8.4 Catálogo de features

```java
FeatureCatalogService catalog = new FeatureCatalogService(
    CustomerFeatures.class,
    ProductFeatures.class,
    TransactionFeatures.class
);

// Listar todas las features
catalog.listAll().forEach(entry -> System.out.printf(
    "%-20s %-8s %-6s %s%n",
    entry.name(), entry.returnTypeName(), entry.ttl(), entry.description()
));

// Búsqueda por nombre o descripción
List<FeatureCatalogEntry> resultados = catalog.search("transaccion");

// Por clase
List<FeatureCatalogEntry> clienteFeatures =
    catalog.findByClass(CustomerFeatures.class.getName());
```

### 8.5 Resolución automática de dependencias

Gauss resuelve el grafo de dependencias entre features automáticamente. Si `normCount` requiere `txCount30d`, el framework garantiza que `txCount30d` se evalúa primero (orden topológico):

```java
FeatureClass fc    = FeatureClass.scan(CustomerFeatures.class);
List<FeatureDescriptor> order = fc.topologicalOrder();
// → [txCount30d, normCount, clv]  (txCount30d antes que normCount)
```

Si hay dependencias cíclicas, se lanza `IllegalStateException("Cyclic dependency detected")`.

---

## 9. gauss-quarkus — Adaptador Quarkus

### 9.1 Registro automático de endpoints

Al arrancar Quarkus, `QuarkusRuntimeAdapter` escanea el CDI context en busca de beans con `@MLEndpoint` y los registra en `MLEndpointRegistry`:

```java
@ApplicationScoped
public class QuarkusRuntimeAdapter implements GaussRuntimeAdapter {

    @Inject
    MLEndpointRegistry registry;

    public void onStart(@Observes StartupEvent evt, BeanManager beanManager) {
        // escanea y registra automáticamente
    }
}
```

### 9.2 Seguridad por defecto

Todos los endpoints requieren autenticación JWT salvo los marcados con `@AnonymousAllowed`:

```java
@MLEndpoint
public class PublicEndpoint {

    @AnonymousAllowed
    public String healthStatus() {
        return "ok";  // público
    }

    @RolesAllowed({"ML_ENGINEER", "DATA_SCIENTIST"})
    public double predict(Input input) {
        return model.predict(input);  // requiere rol
    }
}
```

### 9.3 Configuración OAuth2/OIDC

```properties
# application.properties
dsml.auth.provider   = keycloak
dsml.auth.issuer-url = https://sso.empresa.com/realms/gauss
dsml.auth.client-id  = gauss-app
dsml.auth.scopes     = openid,email,profile
```

```java
OidcProviderRegistry oidcRegistry = new OidcProviderRegistry();
oidcRegistry.register(OidcProviderRegistry.fromProperties(
    Map.of(
        "dsml.auth.provider",   "keycloak",
        "dsml.auth.issuer-url", "https://sso.empresa.com/realms/gauss",
        "dsml.auth.client-id",  "gauss-app"
    )
));

// Mapear roles Keycloak → roles Gauss
OidcProviderDescriptor desc = new OidcProviderDescriptor(
    OidcProviderType.KEYCLOAK,
    "https://sso.empresa.com/realms/gauss",
    "gauss-app",
    List.of("openid", "email"),
    List.of(
        new OidcRoleMapping("ml-engineers", "ML_ENGINEER"),
        new OidcRoleMapping("data-scientists", "DATA_SCIENTIST")
    )
);
```

---

## 10. gauss-lex — Gobernanza y cumplimiento

### 10.1 Audit log inmutable

```java
InMemoryAuditLog auditLog = new InMemoryAuditLog();

// Registrar una acción sensible
auditLog.append(
    AuditEvent.builder(AuditAction.MODEL_PROMOTED)
        .actor("alice")
        .resource("model:churn-v2")
        .namespace("team-alpha")
        .ipAddress("10.0.1.42")
        .details(Map.of("stage", "PRODUCTION", "previousStage", "STAGING"))
        .build()
);

// Consultas
List<AuditEvent> promosByAlice = auditLog.findByActor("alice");
List<AuditEvent> models        = auditLog.findByAction(AuditAction.MODEL_PROMOTED);
List<AuditEvent> lastHour      = auditLog.findBetween(
    Instant.now().minusSeconds(3600), Instant.now());

// Exportar a SIEM en formato CEF
String cef = event.toCef();
// CEF:0|Gauss|Lex|1.0|MODEL_PROMOTED|Model promotion|5|...
```

**Nota:** El SPI `AuditLog` es append-only por diseño; no expone métodos `delete`, `remove` ni `clear`.

### 10.2 Linaje de datos end-to-end

```java
LineageService lineage = new LineageService();

// Durante la ejecución del pipeline:
lineage.recordPipelineExecution("pipe-etl-001", "churn-pipeline",
    "src-postgres-001", "jdbc://customers");

// Durante el cálculo de features:
lineage.recordFeatureComputation("feat-txcount-e42", "txCount30d",
    "customer-42", "pipe-etl-001");

// Tras la predicción:
lineage.recordPrediction("pred-abc-001", "customer-42",
    "model-churn-v2", "churn-v2",
    List.of("feat-txcount-e42", "feat-clv-e42"));

// Trazar el linaje completo
LineageGraph graph = lineage.trace("pred-abc-001");
// Retorna: predicción → modelo → features → pipeline → fuente de datos

graph.nodesByType(LineageNodeType.DATA_SOURCE)
     .forEach(n -> System.out.println("Origen: " + n.name()));
```

### 10.3 GDPR — Retención y derecho al olvido

```java
ComplianceService compliance = new ComplianceService();

// Configurar políticas de retención
compliance.setRetentionPolicy(new RetentionPolicy("predictions",  90));  // 90 días
compliance.setRetentionPolicy(new RetentionPolicy("features",    365));  // 1 año
compliance.setRetentionPolicy(new RetentionPolicy("experiments", 730));  // 2 años
compliance.setRetentionPolicy(new RetentionPolicy("audit_logs",   -1));  // indefinido

// Vincular datos a sujetos
compliance.registerSubjectData("user-42", "predictions", predictionRecord);
compliance.registerSubjectData("user-42", "features",    featureVector);

// Derecho al olvido (GDPR Art. 17)
DeletionCertificate cert = compliance.deleteSubject("user-42");
cert.toText();  // Certificado descargable para el DPO
```

### 10.4 Multi-tenancy y namespacing

```java
// Configurar namespace activo (normalmente en un request filter)
NamespaceContext.set("team-alpha");

// Registrar recursos en su namespace
NamespaceRegistry registry = new NamespaceRegistry();
registry.registerInCurrentNamespace("model:churn-v2");
registry.registerInCurrentNamespace("pipeline:churn-etl");

// Filtrar recursos visibles para el namespace actual
List<String> modelos = registry.visibleResources("model");
// → ["model:churn-v2"]  (no ve recursos de otros equipos)

// Superadmin ve todo
List<String> todos = registry.visibleResources("model", "superadmin");
```

### 10.5 Gestión de secretos

```properties
# Vault (recomendado para producción)
dsml.secrets.provider   = vault
dsml.secrets.vault-url  = https://vault.empresa.com

# Kubernetes Secrets
dsml.secrets.provider = k8s
# Lee de /var/run/secrets/<nombre-del-secreto>

# En memoria (sólo para tests)
dsml.secrets.provider = memory
```

```java
SecretProviderRegistry secrets = new SecretProviderRegistry(
    new K8sSecretProvider(Path.of("/var/run/secrets"))
);

String apiKey  = secrets.getRequired("openai-api-key");  // falla si no existe
String dbPass  = secrets.get("db-password").orElse("");  // vacío si no existe
```

### 10.6 Panel de administración

```java
AdminDashboardService admin = new AdminDashboardService();

// Los módulos reportan su estado
admin.setModelsTotal(24);
admin.setModelsInProduction(3);
admin.setPipelinesScheduled(8);
admin.setExperimentsTotal(1450);
admin.setFeaturesTotal(67);
admin.setNamespacesTotal(5);

admin.setComponentHealth("model-registry",  "UP");
admin.setComponentHealth("feature-store",   "UP");
admin.setComponentHealth("experiment-store","DEGRADED");

// Snapshot para el dashboard
SystemOverview overview = admin.overview();
overview.isHealthy();  // false (hay DEGRADED)
```

---

## 11. gauss-spark — Ejecución distribuida

Para pipelines con datasets que no caben en memoria JVM:

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

```java
// Ejecución — usa Spark si está disponible; cae en local si no
SparkPipelineRunner runner = new SparkPipelineRunner();
SparkJobResult result = runner.run(new BigDataPipeline());

System.out.printf("Ejecutado %s: %d registros procesados en %d ms%n",
    result.executedLocally() ? "localmente" : "en Spark",
    result.recordsWritten(),
    result.duration().toMillis()
);
```

**Configuraciones de master disponibles:**

| Master | Uso |
|--------|-----|
| `local[*]` | Local, todos los núcleos (por defecto) |
| `local[4]` | Local, 4 hilos |
| `spark://host:7077` | Cluster standalone |
| `yarn` | Apache YARN |
| `k8s://https://api:6443` | Kubernetes |

---

## 12. Seguridad y observabilidad

### 12.1 Métricas con Micrometer

Métricas registradas automáticamente por `PredictionMetrics`:

| Métrica | Tags | Descripción |
|---------|------|-------------|
| `dsml.prediction.latency` | `endpoint`, `status` | Timer por llamada |
| `dsml.prediction.count` | `endpoint`, `result` | Contador total |
| `dsml.llm.tokens` | `provider`, `model`, `endpoint` | Tokens LLM consumidos |
| `dsml.circuit.state` | `endpoint` | Estado del circuit breaker |
| `dsml.feature.cache.hit` | `feature` | Hit ratio del feature store |

Disponibles en `/q/metrics` (Prometheus format) y en Grafana con el dashboard JSON incluido.

### 12.2 Trazas con OpenTelemetry

```java
GaussTracer tracer = new GaussTracer(otlpExporter);

// En interceptores o métodos custom:
try (var span = tracer.startSpan("feature-enrichment", "INTERNAL")) {
    span.setAttribute("endpoint", "churn")
        .setAttribute("entity_id", customerId);
    Object result = enrichFeatures(customerId);
    span.recordException(null);  // si falla: .recordException(e)
}
// → span exportado al backend OTLP (Jaeger, Tempo, Zipkin)
```

En tests, usar `InMemorySpanExporter` para aserciones sin OTLP:

```java
InMemorySpanExporter exporter = new InMemorySpanExporter();
GaussTracer tracer = new GaussTracer(exporter);

// Ejecutar código bajo prueba...

assertThat(exporter.spans()).hasSize(2);
assertThat(exporter.findByName("predict")).isPresent();
```

### 12.3 CI/CD con templates generados

Los proyectos Gauss incluyen templates listos para usar:

**`.github/workflows/ci.yml`** (GitHub Actions):
```yaml
- name: Build and test
  run: mvn --no-transfer-progress verify

- name: Verify TypeScript contracts
  run: mvn --no-transfer-progress gauss:verify-ts-contracts
```

**`.gitlab-ci.yml`** (GitLab CI):
```yaml
test:
  script:
    - ./mvnw --no-transfer-progress test

verify-ts-contracts:
  script:
    - ./mvnw --no-transfer-progress gauss:verify-ts-contracts
```

---

## 13. Testing

### 13.1 Tests de pipelines — `GaussPipelineExtension`

```java
@ExtendWith(GaussPipelineExtension.class)
class ChurnPipelineTest {

    @Test
    void transform_computesCorrectFeatures(PipelineTestRunner runner) {
        List<Customer>    customers = List.of(new Customer("c1", 35));
        List<Transaction> txs       = List.of(new Transaction("c1", 100.0));

        PipelineTestResult result = runner
            .withMockSource("customers",    customers)
            .withMockSource("transactions", txs)
            .run("churn-features", new ChurnFeaturePipeline());

        assertThat(result.outputOf("engineer")).isNotNull();
    }
}
```

### 13.2 Tests de endpoints ML — `GaussModelExtension`

```java
@ExtendWith(GaussModelExtension.class)
class ChurnEndpointTest {

    @Test
    void predict_returnsExpectedScore(
            @MockModels MockModels models) {

        // Inyectar modelo mock que devuelve 0.87
        models.register("models/churn.onnx", input -> new float[]{0.87f});

        ChurnEndpoint endpoint = new ChurnEndpoint();
        double score = endpoint.predict(new CustomerInput("c42", 35, "a@b.com"));

        assertThat(score).isCloseTo(0.87, within(0.001));
    }
}
```

### 13.3 Tests de features — `GaussFeatureExtension`

```java
@ExtendWith(GaussFeatureExtension.class)
class CustomerFeaturesTest {

    @Test
    void txCount_cachedAfterFirstCall(
            InMemoryFeatureStore store,
            TestClock clock) {

        CustomerFeatures bean = new CustomerFeatures();
        store.getAll("c-42", bean, CustomerFeatures.class);
        store.getAll("c-42", bean, CustomerFeatures.class);  // segunda llamada

        // La feature sólo se calculó 1 vez (la segunda llegó del caché)
        assertThat(store.computeCount("txCount30d")).isEqualTo(1);
    }

    @Test
    void feature_expiredAfterTtl(
            InMemoryFeatureStore store,
            TestClock clock) {

        CustomerFeatures bean = new CustomerFeatures();
        store.getAll("c-42", bean, CustomerFeatures.class);

        clock.advance(Duration.ofHours(2));  // avanzar más allá del TTL de 1h

        FeatureDescriptor desc = FeatureClass.scan(CustomerFeatures.class)
                                             .find("txCount30d").orElseThrow();
        assertThat(store.get("c-42", desc)).isEmpty();
    }
}
```

### 13.4 `TestClock` — control del tiempo en tests

```java
TestClock clock = new TestClock();  // comienza en Instant.now()
clock.advance(Duration.ofHours(2)); // avanzar tiempo
clock.reset(Instant.parse("2026-01-01T00:00:00Z")); // fijar fecha concreta
```

---

## 14. Referencia de anotaciones

| Anotación | Atributos clave | Descripción |
|-----------|-----------------|-------------|
| `@MLEndpoint` | — | Expone una clase como endpoint HTTP ML |
| `@DataPipeline` | `value` (nombre) | Define un pipeline de datos |
| `@Ingest` | `source` | Punto de entrada de datos del pipeline |
| `@Transform` | — | Paso de transformación en el pipeline |
| `@Scheduled` | `cron`, `description` | Planifica ejecución con expresión cron |
| `@SparkExecution` | `master`, `appName`, `executorMemory`, `executorCores` | Ejecuta pipeline en Spark |
| `@Feature` | `ttl`, `description`, `version` | Define una feature cacheada |
| `@Experiment` | `name`, `tags` | Registra un run de experimento automáticamente |
| `@ModelCard` | `description`, `intendedUse`, `limitations`, `trainedOn`, `version` | Documenta un modelo |
| `@ModelVersion` | `value`, `weight`, `minSampleSize` | Versión de modelo para A/B |
| `@ModelGuardrail` | `metric`, `min`, `max` | Guardrail de calidad para promoción |
| `@BatchPrediction` | `batchSize` | Predicción asíncrona en lote |
| `@CachedPrediction` | `ttl`, `backend` | Caché de predicciones |
| `@CacheEvict` | `cacheName` | Invalida caché de predicciones |
| `@CircuitBreaker` | `threshold`, `delay`, `fallback` | Circuit breaker en endpoint |
| `@AutoRollback` | `metric`, `threshold`, `windowMinutes`, `maxPerHour` | Rollback automático |
| `@DriftMonitor` | `metric`, `threshold`, `sampleSize` | Detección de drift |
| `@LatencySLO` | `p50`, `p95`, `p99` | SLOs de latencia |
| `@Traced` | `operationName`, `kind` | Span OTel en un método |
| `@Explainable` | `topFeatures`, `async` | Explicación SHAP de predicción |
| `@AnonymousAllowed` | — | Endpoint público (sin autenticación) |

---

## 15. Referencia de propiedades

```properties
# ── Runtime ────────────────────────────────────────────────────────────────
dsml.namespace            = default          # Namespace del proyecto
dsml.models.base-path     = models/          # Directorio de archivos ONNX

# ── Seguridad ────────────────────────────────────────────────────────────────
dsml.auth.provider        = jwt              # jwt | keycloak | auth0 | google
dsml.auth.issuer-url      =                  # URL del proveedor OIDC
dsml.auth.client-id       =                  # Client ID de la aplicación
dsml.auth.scopes          = openid,email     # Scopes OAuth2

# ── Secretos ─────────────────────────────────────────────────────────────────
dsml.secrets.provider     = memory           # memory | vault | k8s
dsml.secrets.vault-url    =                  # URL de Vault (si provider=vault)

# ── Experiment tracking ───────────────────────────────────────────────────────
dsml.tracking.backend     = internal         # internal | mlflow
dsml.tracking.url         =                  # URL MLflow (si backend=mlflow)

# ── Retención GDPR ────────────────────────────────────────────────────────────
dsml.retention.predictions = 90d             # TTL de predicciones
dsml.retention.features    = 365d            # TTL de features materializadas
dsml.retention.experiments = 730d            # TTL de experimentos
dsml.retention.audit_logs  = -1              # -1 = retención indefinida

# ── Feature Store ─────────────────────────────────────────────────────────────
dsml.feature-store.backend = caffeine        # caffeine | redis
dsml.feature-store.redis-url =               # URL Redis (si backend=redis)

# ── TypeScript generation ─────────────────────────────────────────────────────
gauss.tsOutputDir          = frontend/generated
gauss.contractsFile        = .gauss-ts-contracts
gauss.updateContracts      = false
gauss.skipVerifyTs         = false

# ── Spark ─────────────────────────────────────────────────────────────────────
# Configurado por anotación @SparkExecution; no hay propiedad global
```

---

## Apéndice A — Dependencias del BOM

```xml
<dependency>
  <groupId>io.gauss</groupId>
  <artifactId>gauss-core</artifactId>        <!-- Anotaciones, NativeImageConfig -->
</dependency>
<dependency>
  <groupId>io.gauss</groupId>
  <artifactId>gauss-vela</artifactId>        <!-- Generación TypeScript -->
</dependency>
<dependency>
  <groupId>io.gauss</groupId>
  <artifactId>gauss-flume</artifactId>       <!-- Pipelines de datos -->
</dependency>
<dependency>
  <groupId>io.gauss</groupId>
  <artifactId>gauss-augur</artifactId>       <!-- Serving ONNX / LLMs -->
</dependency>
<dependency>
  <groupId>io.gauss</groupId>
  <artifactId>gauss-vigil</artifactId>       <!-- Experiment tracking -->
</dependency>
<dependency>
  <groupId>io.gauss</groupId>
  <artifactId>gauss-stratum</artifactId>     <!-- Feature store -->
</dependency>
<dependency>
  <groupId>io.gauss</groupId>
  <artifactId>gauss-quarkus</artifactId>     <!-- Adaptador Quarkus -->
</dependency>
<dependency>
  <groupId>io.gauss</groupId>
  <artifactId>gauss-lex</artifactId>         <!-- Gobernanza y compliance -->
</dependency>
<dependency>
  <groupId>io.gauss</groupId>
  <artifactId>gauss-spark</artifactId>       <!-- Ejecución distribuida (opcional) -->
</dependency>
```

## Apéndice B — Resumen de módulos y tests

| Módulo | Descripción | Tests |
|--------|-------------|-------|
| `gauss-core` | Anotaciones, NativeImageConfig | 8 |
| `gauss-vela` | Generación TypeScript, Zod, OpenAPI | 87 |
| `gauss-flume` | Pipelines ETL, cron scheduler, pipeline status | 92 |
| `gauss-augur` | ONNX, LLMs, caché, circuit breaker, SHAP, drift, SLO, trazas, batch, versioning | 272 |
| `gauss-cli` | CLI `gauss new`, templates CI/CD | 14 |
| `gauss-maven-plugin` | Goal `generate-ts`, `verify-ts-contracts` | 14 |
| `gauss-quarkus` | Adaptador CDI, seguridad, OIDC | 74 |
| `gauss-vigil` | Experiments, Model Registry, guardrails, rollback, A/B stats, metric stream | 163 |
| `gauss-stratum` | Feature store online/offline, catálogo, test utilities | 77 |
| `gauss-lex` | Audit log, linaje, GDPR, secretos, namespacing, admin dashboard | 107 |
| `gauss-spark` | SparkConfig, LocalSparkPipelineRunner, SparkPipelineRunner | 34 |
| **Total** | **13 módulos — 59/59 HUs** | **~942** |

---

*Manual generado para Gauss Framework v0.1.0-SNAPSHOT · Todos los tests en verde · BUILD SUCCESS*

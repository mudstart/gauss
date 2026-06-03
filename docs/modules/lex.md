---
layout: default
title: gauss-lex
parent: Módulos
nav_order: 8
---

# gauss-lex — Gobernanza y cumplimiento
{: .no_toc }

<details open markdown="block">
<summary>Contenido</summary>
{: .text-delta }
1. TOC
{:toc}
</details>

*Lex* (latín: "ley") proporciona la capa de gobernanza para entornos regulados:
audit log, linaje de datos, GDPR, gestión de secretos y multi-tenancy.

---

## Audit log inmutable (ISO 27001)

```java
auditLog.append(
    AuditEvent.builder(AuditAction.MODEL_PROMOTED)
        .actor("alice")
        .resource("model:churn-v2")
        .namespace("team-alpha")
        .ipAddress("10.0.1.42")
        .details(Map.of("stage", "PRODUCTION"))
        .build()
);

// Exportar a SIEM en formato CEF
String cef = event.toCef();
// CEF:0|Gauss|Lex|1.0|MODEL_PROMOTED|Model promotion|5|...
```

{: .important }
El SPI `AuditLog` es **append-only** por diseño: no expone ningún método
`delete`, `remove` ni `clear`. Una vez registrado, un evento no puede
modificarse.

**Acciones disponibles:** `MODEL_PROMOTED`, `MODEL_ARCHIVED`, `MODEL_ROLLED_BACK`,
`PIPELINE_EXECUTED`, `FEATURE_ACCESSED`, `PERMISSION_CHANGED`, `DATA_DELETED`,
`LLM_INPUT_BLOCKED`, `CONFIG_CHANGED`.

---

## Linaje de datos end-to-end (EU AI Act)

```java
LineageService lineage = new LineageService();

// 1. Pipeline ejecutado
lineage.recordPipelineExecution("pipe-001", "churn-pipeline",
    "src-pg-001", "jdbc://customers");

// 2. Feature calculada
lineage.recordFeatureComputation("feat-tx-001", "txCount30d",
    "customer-42", "pipe-001");

// 3. Predicción realizada
lineage.recordPrediction("pred-abc-001", "customer-42",
    "model-churn-v2", "churn-v2",
    List.of("feat-tx-001", "feat-clv-001"));

// 4. Trazar
LineageGraph graph = lineage.trace("pred-abc-001");
// predicción → modelo → features → pipeline → fuente
```

---

## GDPR — Retención y derecho al olvido

```java
// Configurar políticas
compliance.setRetentionPolicy(new RetentionPolicy("predictions",  90));
compliance.setRetentionPolicy(new RetentionPolicy("features",    365));
compliance.setRetentionPolicy(new RetentionPolicy("audit_logs",   -1)); // indefinido

// Vincular datos a sujeto
compliance.registerSubjectData("user-42", "predictions", predRecord);

// Derecho al olvido (GDPR Art. 17)
DeletionCertificate cert = compliance.deleteSubject("user-42");
cert.toText();  // documento descargable para el DPO
```

{: .note }
Los audit logs técnicos están exentos del borrado GDPR según el recital 65 del RGPD.

---

## Gestión de secretos

```properties
# Vault (producción)
dsml.secrets.provider  = vault
dsml.secrets.vault-url = https://vault.empresa.com

# Kubernetes Secrets
dsml.secrets.provider = k8s   # lee de /var/run/secrets/<nombre>

# En memoria (tests y desarrollo local)
dsml.secrets.provider = memory
```

```java
SecretProviderRegistry secrets = new SecretProviderRegistry(
    new K8sSecretProvider(Path.of("/var/run/secrets"))
);

String apiKey = secrets.getRequired("openai-api-key");  // falla al arrancar si no existe
```

---

## Multi-tenancy y namespacing

```java
// Fijar namespace del hilo actual (en un request filter)
NamespaceContext.set("team-alpha");

// Registrar un recurso en el namespace activo
registry.registerInCurrentNamespace("model:churn-v2");

// Sólo ve recursos de su namespace
List<String> modelos = registry.visibleResources("model");

// Superadmin ve todo
List<String> todos = registry.visibleResources("model", "superadmin");
```

---

## Panel de administración

```java
admin.setModelsTotal(24);
admin.setModelsInProduction(3);
admin.setPipelinesScheduled(8);
admin.setComponentHealth("model-registry", "UP");
admin.setComponentHealth("feature-store",  "DEGRADED");

SystemOverview overview = admin.overview();
overview.isHealthy();  // false — hay un componente DEGRADED
```

Expuesto en `GET /dsml/admin/overview` (rol `ADMIN` requerido).

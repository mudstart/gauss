---
layout: default
title: Propiedades
parent: Referencia
nav_order: 2
---

# Referencia de propiedades
{: .no_toc }

Todas las propiedades de configuración de Gauss en `src/main/resources/application.properties`.

---

## Runtime

| Propiedad | Por defecto | Descripción |
|-----------|-------------|-------------|
| `dsml.namespace` | `default` | Namespace del proyecto (multi-tenancy) |
| `dsml.models.base-path` | `models/` | Directorio de archivos `.onnx` |

---

## Seguridad y autenticación

| Propiedad | Por defecto | Descripción |
|-----------|-------------|-------------|
| `dsml.auth.provider` | `jwt` | `jwt` \| `keycloak` \| `auth0` \| `google` \| `github` |
| `dsml.auth.issuer-url` | — | URL del proveedor OIDC |
| `dsml.auth.client-id` | — | Client ID de la aplicación |
| `dsml.auth.scopes` | `openid,email` | Scopes OAuth2 separados por coma |

---

## Gestión de secretos

| Propiedad | Por defecto | Descripción |
|-----------|-------------|-------------|
| `dsml.secrets.provider` | `memory` | `memory` \| `vault` \| `k8s` |
| `dsml.secrets.vault-url` | — | URL base de Vault (cuando `provider=vault`) |

---

## Experiment tracking

| Propiedad | Por defecto | Descripción |
|-----------|-------------|-------------|
| `dsml.tracking.backend` | `internal` | `internal` \| `mlflow` |
| `dsml.tracking.url` | — | URL del servidor MLflow |

---

## Retención de datos (GDPR)

| Propiedad | Por defecto | Descripción |
|-----------|-------------|-------------|
| `dsml.retention.predictions` | `90d` | TTL de registros de predicción |
| `dsml.retention.features` | `365d` | TTL de features materializadas |
| `dsml.retention.experiments` | `730d` | TTL de runs de experimentos |
| `dsml.retention.audit_logs` | `-1` | `-1` = retención indefinida |

---

## Feature Store

| Propiedad | Por defecto | Descripción |
|-----------|-------------|-------------|
| `dsml.feature-store.backend` | `caffeine` | `caffeine` \| `redis` |
| `dsml.feature-store.redis-url` | — | URL Redis (cuando `backend=redis`) |

---

## Plugin Maven — gauss-maven-plugin

| Propiedad | Por defecto | Descripción |
|-----------|-------------|-------------|
| `gauss.tsOutputDir` | `frontend/generated` | Directorio de salida TypeScript |
| `gauss.contractsFile` | `.gauss-ts-contracts` | Archivo de checksums de contratos |
| `gauss.updateContracts` | `false` | `true` para regenerar checksums |
| `gauss.skipVerifyTs` | `false` | `true` para saltar verificación |

---

## Ejemplo completo

```properties
# ─── Runtime ──────────────────────────────────────────────
dsml.namespace         = equipo-alpha
dsml.models.base-path  = models/

# ─── Autenticación (Keycloak) ─────────────────────────────
dsml.auth.provider     = keycloak
dsml.auth.issuer-url   = https://sso.empresa.com/realms/gauss
dsml.auth.client-id    = gauss-app
dsml.auth.scopes       = openid,email,profile

# ─── Secretos (Kubernetes) ────────────────────────────────
dsml.secrets.provider  = k8s

# ─── Experiment tracking ──────────────────────────────────
dsml.tracking.backend  = internal

# ─── Retención GDPR ───────────────────────────────────────
dsml.retention.predictions  = 90d
dsml.retention.features     = 365d
dsml.retention.experiments  = 730d
dsml.retention.audit_logs   = -1

# ─── Feature Store (Redis en producción) ──────────────────
dsml.feature-store.backend   = redis
dsml.feature-store.redis-url = redis://redis:6379
```

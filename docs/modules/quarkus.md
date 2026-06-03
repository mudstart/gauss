---
layout: default
title: gauss-quarkus
parent: Módulos
nav_order: 7
---

# gauss-quarkus — Adaptador Quarkus
{: .no_toc }

Registra automáticamente los beans `@MLEndpoint` en Quarkus al arrancar, aplica
la seguridad por defecto y gestiona la configuración OAuth2/OIDC.

---

## Seguridad por defecto

Todos los endpoints requieren autenticación JWT salvo los marcados con `@AnonymousAllowed`:

```java
@MLEndpoint
public class ChurnEndpoint {

    @AnonymousAllowed
    public String status() { return "ok"; }           // público

    @RolesAllowed({"ML_ENGINEER", "DATA_SCIENTIST"})
    public double predict(CustomerInput input) { ... } // requiere rol
}
```

---

## Configuración OAuth2/OIDC

```properties
dsml.auth.provider   = keycloak
dsml.auth.issuer-url = https://sso.empresa.com/realms/gauss
dsml.auth.client-id  = gauss-app
dsml.auth.scopes     = openid,email,profile
```

### Mapeo de roles

```java
OidcProviderDescriptor desc = new OidcProviderDescriptor(
    OidcProviderType.KEYCLOAK,
    "https://sso.empresa.com/realms/gauss", "gauss-app",
    List.of("openid", "email"),
    List.of(
        new OidcRoleMapping("ml-engineers",    "ML_ENGINEER"),
        new OidcRoleMapping("data-scientists", "DATA_SCIENTIST"),
        new OidcRoleMapping("admins",          "ADMIN")
    )
);
```

**Proveedores soportados:** `KEYCLOAK`, `AUTH0`, `GOOGLE`, `GITHUB`, `CUSTOM`.

---

## Live reload (dev mode)

`ClassFileWatcher` detecta cambios en clases `@MLEndpoint` y dispara la
regeneración automática del cliente TypeScript.

```bash
mvn quarkus:dev    # inicia con live reload y regeneración TS activa
```

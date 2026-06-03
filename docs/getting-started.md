---
layout: default
title: Instalación y primer proyecto
nav_order: 2
---

# Instalación y primer proyecto
{: .no_toc }

<details open markdown="block">
<summary>Contenido</summary>
{: .text-delta }
1. TOC
{:toc}
</details>

---

## Requisitos

| Herramienta | Versión mínima | Notas |
|-------------|----------------|-------|
| Java JDK | **21** | Requerido: records, pattern matching |
| Maven | **3.9** | Incluye el plugin `gauss-maven-plugin` |
| Node.js | **20** | Solo para el frontend generado |
| Docker | 24 | Opcional, para despliegue en contenedor |

---

## Instalar Gauss en el repositorio local

```bash
git clone https://github.com/gauss-framework/gauss.git
cd gauss
mvn install -DskipTests     # ~30s en un MacBook M1
```

Esto publica todos los módulos (`gauss-core`, `gauss-vela`, `gauss-flume`, etc.)
en el repositorio Maven local (`~/.m2/repository/io/gauss/`).

---

## Crear un proyecto nuevo

### Con la CLI `gauss`

```bash
# Quarkus (runtime recomendado)
gauss new mi-modelo --group-id=com.empresa --runtime=quarkus

# Spring Boot
gauss new mi-modelo --group-id=com.empresa --runtime=spring
```

### Con el plugin Maven

```bash
mvn io.gauss:gauss-maven-plugin:new \
    -Dname=mi-modelo \
    -DgroupId=com.empresa \
    -Druntime=quarkus
```

---

## Estructura del proyecto generado

```
mi-modelo/
├── pom.xml
├── README.md
├── .gitignore
├── .github/workflows/ci.yml        ← GitHub Actions
├── .gitlab-ci.yml                   ← GitLab CI
├── .gauss-ts-contracts              ← checksums de contratos TypeScript
│
├── src/main/java/com/empresa/mimodelo/
│   ├── GreetingEndpoint.java        ← ejemplo @MLEndpoint
│   ├── model/                       ← DTOs Java
│   ├── pipeline/ChurnPipeline.java  ← ejemplo @DataPipeline
│   ├── features/CustomerFeatures.java ← ejemplo @Feature
│   └── training/ChurnExperiment.java  ← ejemplo @Experiment
│
├── frontend/
│   ├── package.json
│   ├── src/generated/               ← TypeScript generado automáticamente
│   └── src/App.tsx
│
├── pipelines/                       ← configuración YAML de pipelines
└── models/                          ← archivos .onnx
```

---

## Modo desarrollo

```bash
cd mi-modelo

# Backend Quarkus con live reload
mvn quarkus:dev

# Frontend (en otra terminal)
cd frontend
npm install
npm run dev
```

El servidor arranca en `http://localhost:8080`. La Dev UI de Quarkus está en
`http://localhost:8080/q/dev`.

Cada vez que modificas una clase `@MLEndpoint`, Gauss regenera automáticamente
el cliente TypeScript y Vite detecta el cambio (hot-module replacement).

---

## Adopción modular con el BOM

Para añadir Gauss a un proyecto existente sin una migración completa:

```xml
<!-- pom.xml -->
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

<dependencies>
  <!-- Solo los módulos que necesitas -->
  <dependency>
    <groupId>io.gauss</groupId>
    <artifactId>gauss-core</artifactId>
  </dependency>
  <dependency>
    <groupId>io.gauss</groupId>
    <artifactId>gauss-vigil</artifactId>   <!-- experiment tracking -->
  </dependency>
  <dependency>
    <groupId>io.gauss</groupId>
    <artifactId>gauss-stratum</artifactId> <!-- feature store -->
  </dependency>
</dependencies>
```

---

## Verificar la instalación

```bash
# Compilar y ejecutar todos los tests
mvn clean test --no-transfer-progress

# Debe terminar con:
# [INFO] BUILD SUCCESS
# Tests run: ~942, Failures: 0, Errors: 0
```

---

## Próximos pasos

- [Exponer tu primer modelo →]({{ site.baseurl }}/modules/augur)
- [Definir features →]({{ site.baseurl }}/modules/stratum)
- [Registrar experimentos →]({{ site.baseurl }}/modules/vigil)

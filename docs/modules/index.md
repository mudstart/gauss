---
layout: default
title: Módulos
nav_order: 3
has_children: true
---

# Módulos de Gauss

Gauss está organizado en 13 módulos Maven independientes. Cada módulo puede usarse
de forma aislada a través del BOM `gauss-bom`.

| Módulo | Capa | SP | Tests |
|--------|------|----|-------|
| [gauss-core](core) | Anotaciones | 26 | 8 |
| [gauss-vela](vela) | Generación TypeScript | 55 | 87 |
| [gauss-flume](flume) | Pipelines ETL | 65 | 92 |
| [gauss-augur](augur) | Serving modelos | 102 | 272 |
| [gauss-vigil](vigil) | Experiment tracking | 50 | 163 |
| [gauss-stratum](stratum) | Feature store | 44 | 77 |
| [gauss-quarkus](quarkus) | Runtime Quarkus | 37 | 74 |
| [gauss-lex](lex) | Gobernanza / compliance | 50 | 107 |
| [gauss-spark](spark) | Distribución Spark | 21 | 34 |
| gauss-cli | CLI de scaffolding | 8 | 14 |
| gauss-maven-plugin | Plugin Maven | 8 | 14 |
| gauss-bom | BOM de dependencias | — | — |

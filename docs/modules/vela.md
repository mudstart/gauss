---
layout: default
title: gauss-vela
parent: Módulos
nav_order: 2
---

# gauss-vela — Generación de TypeScript
{: .no_toc }

<details open markdown="block">
<summary>Contenido</summary>
{: .text-delta }
1. TOC
{:toc}
</details>

`gauss-vela` lee las clases `@MLEndpoint` en tiempo de build y genera:
interfaces TypeScript, funciones cliente `async`, esquemas Zod y especificación OpenAPI 3.0.

---

## Generación automática en build

```xml
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
mvn gauss:generate-ts             # generación manual
mvn gauss:verify-ts-contracts     # verificar checksums en CI
mvn gauss:verify-ts-contracts -Dgauss.updateContracts=true  # actualizar checksums
```

---

## Mapeo de tipos Java → TypeScript

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

---

## Ejemplo completo

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

    public ChurnResult predict(CustomerInput input) { ... }
}
```

**TypeScript generado:**
```typescript
export interface CustomerInput { customerId: string; age: number; email: string; }
export interface ChurnResult   { probability: number; reason: string; }

export const CustomerInputSchema = z.object({
  customerId: z.string().min(1),
  age:        z.number().min(0),
  email:      z.string().email(),
});

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

---

## Streaming reactivo

```java
// Java — método que retorna Multi<T>
public Multi<String> generate(String prompt) {
    return aiService.streamGenerate(prompt);
}
```

```typescript
// TypeScript generado — AsyncIterable
for await (const token of generate({ prompt: "Hola" })) {
  setOutput(prev => prev + token);
}
```

---

## Verificación de contratos en CI

El goal `verify-ts-contracts` compara el hash SHA-256 de los archivos `.ts` generados
con los checksums almacenados en `.gauss-ts-contracts`. El build falla si algún tipo
ha cambiado sin actualizar el contrato:

```
TypeScript contract violation(s) detected!
  CHANGED: ChurnEndpoint.ts
    expected: a3f1...
    actual:   b7c9...

Run with -Dgauss.updateContracts=true to update the reference checksums.
```

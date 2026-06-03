package io.gauss.vela.generator;

import io.gauss.core.annotation.AnonymousAllowed;
import io.gauss.core.annotation.MLEndpoint;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Generates an OpenAPI 3.0.3 JSON specification from
 * {@link MLEndpoint @MLEndpoint}-annotated classes.
 *
 * <p>Each public non-{@code Object} method becomes a {@code POST} operation.
 * Method parameters are collected into a JSON request body; the return type
 * becomes the {@code 200} response schema.
 *
 * <p>Usage:
 * <pre>{@code
 * OpenApiGenerator gen = new OpenApiGenerator("My ML API", "1.0.0");
 * String json = gen.generate(List.of(ChurnService.class, NlpService.class));
 * }</pre>
 */
public class OpenApiGenerator {

    private final String apiTitle;
    private final String apiVersion;

    public OpenApiGenerator(String apiTitle, String apiVersion) {
        this.apiTitle   = apiTitle;
        this.apiVersion = apiVersion;
    }

    /** Convenience constructor with default title/version. */
    public OpenApiGenerator() {
        this("Gauss ML API", "1.0.0");
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Generates an OpenAPI 3.0.3 JSON document for the given endpoint classes.
     *
     * @param endpointClasses classes annotated with {@code @MLEndpoint}
     * @return pretty-printed JSON string
     * @throws IllegalArgumentException if any class lacks {@code @MLEndpoint}
     */
    public String generate(List<Class<?>> endpointClasses) {
        Map<String, Object> spec     = new LinkedHashMap<>();
        Map<String, Object> paths    = new LinkedHashMap<>();
        Map<String, Object> schemas  = new LinkedHashMap<>();

        spec.put("openapi", "3.0.3");
        spec.put("info",    info());
        spec.put("paths",   paths);
        spec.put("components", components(schemas));

        for (Class<?> cls : endpointClasses) {
            validate(cls);
            processClass(cls, paths, schemas);
        }

        return toJson(spec, 0);
    }

    // -------------------------------------------------------------------------
    // Processing
    // -------------------------------------------------------------------------

    private void validate(Class<?> cls) {
        if (!cls.isAnnotationPresent(MLEndpoint.class)) {
            throw new IllegalArgumentException(
                    cls.getName() + " is not annotated with @MLEndpoint");
        }
    }

    private void processClass(Class<?> cls,
                               Map<String, Object> paths,
                               Map<String, Object> schemas) {
        MLEndpoint ann = cls.getAnnotation(MLEndpoint.class);
        String name    = ann.value().isBlank()
                ? cls.getSimpleName()
                : ann.value();
        String base    = ann.path().isBlank()
                ? "/api/" + camelToKebab(name)
                : ann.path();

        boolean classAnon = cls.isAnnotationPresent(AnonymousAllowed.class);

        for (Method method : publicEndpointMethods(cls)) {
            String path = base + "/" + method.getName();
            paths.put(path, buildPathItem(method, name, classAnon, schemas));
        }
    }

    private Map<String, Object> buildPathItem(Method method,
                                               String endpointName,
                                               boolean classAnon,
                                               Map<String, Object> schemas) {
        Map<String, Object> post = new LinkedHashMap<>();
        post.put("summary",     endpointName + "." + method.getName());
        post.put("operationId", endpointName + "_" + method.getName());
        post.put("tags",        List.of(endpointName));

        // Security
        boolean anonMethod = method.isAnnotationPresent(AnonymousAllowed.class);
        if (!classAnon && !anonMethod) {
            post.put("security", List.of(Map.of("bearerAuth", List.of())));
        }

        // Request body (if the method has parameters)
        if (method.getParameterCount() > 0) {
            post.put("requestBody", requestBody(method, schemas));
        }

        // Response
        post.put("responses", responses(method.getGenericReturnType(), schemas));

        Map<String, Object> pathItem = new LinkedHashMap<>();
        pathItem.put("post", post);
        return pathItem;
    }

    private Map<String, Object> requestBody(Method method,
                                             Map<String, Object> schemas) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (Parameter param : method.getParameters()) {
            properties.put(param.getName(), typeSchema(param.getParameterizedType(), schemas));
            required.add(param.getName());
        }

        Map<String, Object> bodySchema = new LinkedHashMap<>();
        bodySchema.put("type",       "object");
        bodySchema.put("properties", properties);
        if (!required.isEmpty()) bodySchema.put("required", required);

        return Map.of(
                "required", true,
                "content", Map.of(
                        "application/json", Map.of("schema", bodySchema)));
    }

    private Map<String, Object> responses(Type returnType,
                                           Map<String, Object> schemas) {
        Map<String, Object> twoHundred = new LinkedHashMap<>();
        twoHundred.put("description", "Successful inference result");
        if (returnType != void.class && returnType != Void.class) {
            twoHundred.put("content", Map.of(
                    "application/json",
                    Map.of("schema", typeSchema(returnType, schemas))));
        }

        Map<String, Object> responses = new LinkedHashMap<>();
        responses.put("200",  twoHundred);
        responses.put("401",  Map.of("description", "Unauthorized"));
        responses.put("500",  Map.of("description", "Internal inference error"));
        return responses;
    }

    // -------------------------------------------------------------------------
    // Schema mapping
    // -------------------------------------------------------------------------

    private Map<String, Object> typeSchema(Type type,
                                            Map<String, Object> schemas) {
        if (type instanceof Class<?> cls) {
            return classSchema(cls, schemas);
        }
        // Parameterized types (List<T>, Map<K,V>, etc.) → simplified inline
        return Map.of("type", "object");
    }

    private Map<String, Object> classSchema(Class<?> cls,
                                             Map<String, Object> schemas) {
        if (cls == void.class || cls == Void.class) return Map.of();
        if (cls == String.class || cls == Character.class || cls == char.class)
            return Map.of("type", "string");
        if (cls == boolean.class || cls == Boolean.class)
            return Map.of("type", "boolean");
        if (isNumber(cls)) return Map.of("type", "number");

        if (cls.isArray()) {
            return Map.of(
                    "type",  "array",
                    "items", classSchema(cls.getComponentType(), schemas));
        }

        if (cls == List.class || Collection.class.isAssignableFrom(cls)) {
            return Map.of("type", "array", "items", Map.of("type", "object"));
        }

        if (cls == Map.class || java.util.Map.class.isAssignableFrom(cls)) {
            return Map.of("type", "object", "additionalProperties", true);
        }

        // Complex type → component schema reference
        String ref = "#/components/schemas/" + cls.getSimpleName();
        if (!schemas.containsKey(cls.getSimpleName())) {
            schemas.put(cls.getSimpleName(), buildObjectSchema(cls));
        }
        return Map.of("$ref", ref);
    }

    private Map<String, Object> buildObjectSchema(Class<?> cls) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        for (var field : cls.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            // Use a simplified schema for nested fields (avoid infinite recursion)
            properties.put(field.getName(),
                    primitiveOrRef(field.getType()));
        }
        if (!properties.isEmpty()) schema.put("properties", properties);
        return schema;
    }

    private Map<String, Object> primitiveOrRef(Class<?> cls) {
        if (cls == String.class) return Map.of("type", "string");
        if (isNumber(cls))       return Map.of("type", "number");
        if (cls == boolean.class || cls == Boolean.class) return Map.of("type", "boolean");
        if (cls.isArray())       return Map.of("type", "array", "items", Map.of("type", "number"));
        return Map.of("type", "object");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static List<Method> publicEndpointMethods(Class<?> cls) {
        List<Method> methods = new ArrayList<>();
        for (Method m : cls.getMethods()) {
            if (m.getDeclaringClass() == Object.class) continue;
            if (!Modifier.isPublic(m.getModifiers())) continue;
            methods.add(m);
        }
        return methods;
    }

    private static boolean isNumber(Class<?> cls) {
        return cls == byte.class   || cls == Byte.class    ||
               cls == short.class  || cls == Short.class   ||
               cls == int.class    || cls == Integer.class ||
               cls == long.class   || cls == Long.class    ||
               cls == float.class  || cls == Float.class   ||
               cls == double.class || cls == Double.class;
    }

    private static String camelToKebab(String s) {
        return s.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }

    private Map<String, Object> info() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("title",   apiTitle);
        info.put("version", apiVersion);
        return info;
    }

    private static Map<String, Object> components(Map<String, Object> schemas) {
        Map<String, Object> comp = new LinkedHashMap<>();
        comp.put("securitySchemes", Map.of(
                "bearerAuth", Map.of("type", "http", "scheme", "bearer",
                        "bearerFormat", "JWT")));
        comp.put("schemas", schemas);
        return comp;
    }

    // -------------------------------------------------------------------------
    // Minimal JSON serializer (avoids Jackson dependency in gauss-vela)
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static String toJson(Object value, int indent) {
        if (value == null)                return "null";
        if (value instanceof Boolean b)   return b.toString();
        if (value instanceof Number n)    return n.toString();
        if (value instanceof String s)    return "\"" + escape(s) + "\"";

        String pad  = "  ".repeat(indent);
        String pad1 = "  ".repeat(indent + 1);

        if (value instanceof List<?> list) {
            if (list.isEmpty()) return "[]";
            StringBuilder sb = new StringBuilder("[\n");
            for (int i = 0; i < list.size(); i++) {
                sb.append(pad1).append(toJson(list.get(i), indent + 1));
                if (i < list.size() - 1) sb.append(',');
                sb.append('\n');
            }
            return sb.append(pad).append(']').toString();
        }

        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) return "{}";
            List<Map.Entry<?, ?>> entries = new ArrayList<>((Set<Map.Entry<?, ?>>) (Set<?>) map.entrySet());
            StringBuilder sb = new StringBuilder("{\n");
            for (int i = 0; i < entries.size(); i++) {
                var entry = entries.get(i);
                sb.append(pad1)
                  .append("\"").append(escape(entry.getKey().toString())).append("\": ")
                  .append(toJson(entry.getValue(), indent + 1));
                if (i < entries.size() - 1) sb.append(',');
                sb.append('\n');
            }
            return sb.append(pad).append('}').toString();
        }

        return "\"" + escape(value.toString()) + "\"";
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}

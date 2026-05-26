package io.gauss.vela.generator;

import io.gauss.vela.mapper.JavaTypeMapper;
import io.gauss.vela.model.TsType;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates TypeScript {@code interface} declarations from Java classes.
 *
 * <p>For enums, emits a TypeScript {@code enum} block instead.
 * Fields that are static or annotated with {@code @JsonIgnore} are skipped.
 * Nested DTO types referenced as field types are collected via {@link #referencedTypes()}.
 */
public final class InterfaceGenerator {

    private final JavaTypeMapper mapper;
    private final Set<Class<?>> referenced = new LinkedHashSet<>();

    public InterfaceGenerator() {
        this(JavaTypeMapper.INSTANCE);
    }

    InterfaceGenerator(JavaTypeMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Generates the TypeScript source for {@code cls}.
     *
     * @param cls a POJO or enum class
     * @return TypeScript source fragment (interface or enum block)
     */
    public String generate(Class<?> cls) {
        if (cls.isEnum()) return generateEnum(cls);
        return generateInterface(cls);
    }

    /**
     * Returns non-primitive types encountered as field types during the last
     * {@link #generate(Class)} call — callers can iterate to generate dependencies.
     */
    public Set<Class<?>> referencedTypes() {
        return Set.copyOf(referenced);
    }

    // -------------------------------------------------------------------------

    private String generateInterface(Class<?> cls) {
        referenced.clear();
        List<String> lines = new ArrayList<>();

        for (Field field : cls.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            if (isIgnored(field)) continue;

            TsType tsType = mapper.map(field.getGenericType(), field);
            trackReference(field);
            lines.add("  " + field.getName() + ": " + tsType.render() + ";");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("export interface ").append(cls.getSimpleName()).append(" {\n");
        lines.forEach(l -> sb.append(l).append('\n'));
        sb.append('}');
        return sb.toString();
    }

    private String generateEnum(Class<?> cls) {
        StringBuilder sb = new StringBuilder();
        sb.append("export enum ").append(cls.getSimpleName()).append(" {\n");
        Object[] constants = cls.getEnumConstants();
        for (int i = 0; i < constants.length; i++) {
            sb.append("  ").append(constants[i]);
            if (i < constants.length - 1) sb.append(',');
            sb.append('\n');
        }
        sb.append('}');
        return sb.toString();
    }

    private void trackReference(Field field) {
        Class<?> raw = rawType(field.getType());
        if (raw == null) return;
        if (raw.isPrimitive()) return;
        if (raw.getPackageName().startsWith("java.")) return;
        referenced.add(raw);
    }

    private static Class<?> rawType(Class<?> cls) {
        if (cls.isArray()) return rawType(cls.getComponentType());
        return cls;
    }

    private static boolean isIgnored(Field field) {
        for (var ann : field.getAnnotations()) {
            if (ann.annotationType().getSimpleName().equals("JsonIgnore")) return true;
        }
        return false;
    }
}

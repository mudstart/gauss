package io.gauss.vela.mapper;

import io.gauss.vela.model.TsType;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Maps Java {@link Type} instances to {@link TsType} descriptors.
 *
 * <p>Supports primitives, boxed types, {@code List<T>→T[]}, {@code Map<K,V>→Record<K,V>},
 * {@code Optional<T>→T|undefined}, and detects {@code @Nullable}/{@code @NonNull}
 * annotations on the annotated element (field, parameter) when provided.
 */
public final class JavaTypeMapper {

    public static final JavaTypeMapper INSTANCE = new JavaTypeMapper();

    private JavaTypeMapper() {}

    /**
     * Maps a raw type without annotation context.
     */
    public TsType map(Type javaType) {
        return map(javaType, null);
    }

    /**
     * Maps a type, honouring {@code @Nullable} / {@code @NonNull} on {@code element}.
     *
     * @param javaType the Java type to convert
     * @param element  the annotated element (field, parameter) — may be {@code null}
     */
    public TsType map(Type javaType, AnnotatedElement element) {
        TsType base = resolve(javaType);

        // Optional<T> already carries | undefined semantics — don't double-apply
        if (isOptional(javaType)) return base;

        if (isNullableAnnotated(element))    return base.orNull();
        if (isNonNullAnnotated(element))     return base;          // explicitly non-null

        return base;
    }

    // -------------------------------------------------------------------------
    // Internal resolution
    // -------------------------------------------------------------------------

    private TsType resolve(Type type) {
        if (type instanceof Class<?> cls) return resolveClass(cls);
        if (type instanceof ParameterizedType pt) return resolveParameterized(pt);
        if (type instanceof WildcardType wt) return resolveWildcard(wt);
        // TypeVariable, GenericArrayType → fall back to unknown
        return TsType.of("unknown");
    }

    private TsType resolveClass(Class<?> cls) {
        // Primitives — never null
        if (cls == void.class || cls == Void.class) return TsType.of("void");
        if (cls == boolean.class || cls == Boolean.class) return TsType.of("boolean");
        if (cls == String.class || cls == Character.class || cls == char.class)
            return TsType.of("string");
        if (isNumberClass(cls)) return TsType.of("number");
        if (cls == Object.class) return TsType.of("unknown");

        // Arrays
        if (cls.isArray()) {
            TsType element = resolveClass(cls.getComponentType());
            return TsType.of(element.base() + "[]");
        }

        // Enums → string union in render; we track them as their simple name
        if (cls.isEnum()) return TsType.of(cls.getSimpleName());

        // Everything else (DTOs, etc.) → simple class name
        return TsType.of(cls.getSimpleName());
    }

    private TsType resolveParameterized(ParameterizedType pt) {
        Class<?> raw = (Class<?>) pt.getRawType();
        Type[] args  = pt.getActualTypeArguments();

        // Optional<T> → T | undefined
        if (Optional.class.isAssignableFrom(raw) && args.length == 1) {
            TsType inner = resolve(args[0]);
            return inner.orUndefined();
        }

        // Collection<T> (List, Set, …) → T[]
        if (Collection.class.isAssignableFrom(raw) && args.length == 1) {
            TsType element = resolve(args[0]);
            return TsType.of(element.base() + "[]");
        }

        // Map<K,V> → Record<K, V>
        if (Map.class.isAssignableFrom(raw) && args.length == 2) {
            String key   = resolve(args[0]).base();
            String value = resolve(args[1]).base();
            return TsType.of("Record<" + key + ", " + value + ">");
        }

        // Fallback: RawType<A, B, …>
        StringBuilder sb = new StringBuilder(raw.getSimpleName()).append('<');
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(resolve(args[i]).base());
        }
        sb.append('>');
        return TsType.of(sb.toString());
    }

    private TsType resolveWildcard(WildcardType wt) {
        Type[] upper = wt.getUpperBounds();
        if (upper.length > 0 && upper[0] != Object.class) return resolve(upper[0]);
        return TsType.of("unknown");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean isNumberClass(Class<?> cls) {
        return cls == byte.class   || cls == Byte.class    ||
               cls == short.class  || cls == Short.class   ||
               cls == int.class    || cls == Integer.class ||
               cls == long.class   || cls == Long.class    ||
               cls == float.class  || cls == Float.class   ||
               cls == double.class || cls == Double.class;
    }

    private static boolean isOptional(Type type) {
        if (!(type instanceof ParameterizedType pt)) return false;
        return Optional.class.isAssignableFrom((Class<?>) pt.getRawType());
    }

    private static boolean isNullableAnnotated(AnnotatedElement element) {
        if (element == null) return false;
        return hasAnnotationNamed(element, "Nullable");
    }

    private static boolean isNonNullAnnotated(AnnotatedElement element) {
        if (element == null) return false;
        return hasAnnotationNamed(element, "NonNull") || hasAnnotationNamed(element, "Nonnull");
    }

    private static boolean hasAnnotationNamed(AnnotatedElement element, String simpleName) {
        for (var ann : element.getAnnotations()) {
            if (ann.annotationType().getSimpleName().equals(simpleName)) return true;
        }
        return false;
    }
}

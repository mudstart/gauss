package io.gauss.vela.scanner;

import io.gauss.core.annotation.MLEndpoint;
import io.gauss.vela.mapper.JavaTypeMapper;
import io.gauss.vela.model.EndpointMethod;
import io.gauss.vela.model.ReactiveKind;
import io.gauss.vela.model.TsParameter;
import io.gauss.vela.model.TsType;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Scans a {@code @MLEndpoint}-annotated class and returns its public methods
 * as {@link EndpointMethod} descriptors, sorted alphabetically for determinism.
 */
public final class EndpointScanner {

    private final JavaTypeMapper mapper;

    public EndpointScanner() {
        this(JavaTypeMapper.INSTANCE);
    }

    EndpointScanner(JavaTypeMapper mapper) {
        this.mapper = mapper;
    }

    public List<EndpointMethod> scan(Class<?> endpointClass) {
        MLEndpoint ann = endpointClass.getAnnotation(MLEndpoint.class);
        String classPath = ann != null ? normalizePath(ann.path().isEmpty() ? ann.value() : ann.path()) : "";

        List<EndpointMethod> result = new ArrayList<>();
        for (Method method : endpointClass.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) continue;
            if (isObjectMethod(method)) continue;
            result.add(toEndpointMethod(method, classPath));
        }
        result.sort(Comparator.comparing(EndpointMethod::name));
        return List.copyOf(result);
    }

    // -------------------------------------------------------------------------

    private EndpointMethod toEndpointMethod(Method method, String classPath) {
        Parameter[] params = method.getParameters();
        List<TsParameter> tsParams = new ArrayList<>();
        for (Parameter p : params) {
            TsType type = mapper.map(p.getParameterizedType(), p);
            tsParams.add(new TsParameter(p.getName(), type));
        }

        Type genericReturn      = method.getGenericReturnType();
        ReactiveKind reactiveKind = detectReactiveKind(genericReturn);
        TsType returnType       = reactiveKind.isStreaming()
                ? innerTypeOfReactive(genericReturn)
                : mapper.map(genericReturn);

        String httpMethod = params.length == 0 ? "GET" : "POST";
        String path = classPath + "/" + method.getName();

        return new EndpointMethod(method.getName(), httpMethod, path,
                List.copyOf(tsParams), returnType, reactiveKind);
    }

    // -------------------------------------------------------------------------
    // Reactive type detection
    // -------------------------------------------------------------------------

    private static ReactiveKind detectReactiveKind(Type type) {
        String rawName = rawClassName(type);
        if (rawName == null) return ReactiveKind.NONE;
        if (rawName.equals("io.smallrye.mutiny.Multi"))  return ReactiveKind.MULTI;
        if (rawName.equals("reactor.core.publisher.Flux")) return ReactiveKind.FLUX;
        return ReactiveKind.NONE;
    }

    private TsType innerTypeOfReactive(Type type) {
        if (type instanceof ParameterizedType pt && pt.getActualTypeArguments().length == 1)
            return mapper.map(pt.getActualTypeArguments()[0]);
        return TsType.of("unknown");
    }

    private static String rawClassName(Type type) {
        if (type instanceof Class<?> cls)          return cls.getName();
        if (type instanceof ParameterizedType pt)  return ((Class<?>) pt.getRawType()).getName();
        return null;
    }

    private static String normalizePath(String path) {
        if (path.isEmpty()) return "";
        return path.startsWith("/") ? path : "/" + path;
    }

    private static boolean isObjectMethod(Method method) {
        return Arrays.stream(Object.class.getMethods())
                .anyMatch(om -> om.getName().equals(method.getName()) &&
                                Arrays.equals(om.getParameterTypes(), method.getParameterTypes()));
    }
}

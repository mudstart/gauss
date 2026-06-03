package io.gauss.stratum.test;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * JUnit 5 extension that injects a fresh {@link InMemoryFeatureStore} and its
 * backing {@link TestClock} into test method parameters (HU-040).
 *
 * <p>Usage:
 * <pre>{@code
 * @ExtendWith(GaussFeatureExtension.class)
 * class MyFeatureTest {
 *
 *     @Test
 *     void testTtlExpiry(InMemoryFeatureStore store, TestClock clock) {
 *         store.put(entityId, feature, value, clock.now().plusSeconds(60));
 *         clock.advance(Duration.ofSeconds(90));
 *         assertThat(store.get(entityId, feature)).isEmpty();
 *     }
 * }
 * }</pre>
 *
 * <p>A fresh store and clock are created before each test method, guaranteeing
 * full isolation between tests.
 */
public final class GaussFeatureExtension implements BeforeEachCallback, ParameterResolver {

    private static final ExtensionContext.Namespace NS =
            ExtensionContext.Namespace.create(GaussFeatureExtension.class);

    private static final String STORE_KEY = "store";

    // -------------------------------------------------------------------------

    @Override
    public void beforeEach(ExtensionContext context) {
        TestClock          clock = new TestClock();
        InMemoryFeatureStore store = new InMemoryFeatureStore(clock);
        context.getStore(NS).put(STORE_KEY, store);
    }

    @Override
    public boolean supportsParameter(ParameterContext paramCtx, ExtensionContext extCtx) {
        Class<?> type = paramCtx.getParameter().getType();
        return type == InMemoryFeatureStore.class || type == TestClock.class;
    }

    @Override
    public Object resolveParameter(ParameterContext paramCtx, ExtensionContext extCtx) {
        InMemoryFeatureStore store =
                (InMemoryFeatureStore) extCtx.getStore(NS).get(STORE_KEY);
        Class<?> type = paramCtx.getParameter().getType();
        if (type == InMemoryFeatureStore.class) return store;
        if (type == TestClock.class)             return store.clock();
        throw new IllegalStateException("Unsupported parameter type: " + type);
    }
}

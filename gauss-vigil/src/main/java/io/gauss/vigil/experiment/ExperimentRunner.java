package io.gauss.vigil.experiment;

import io.gauss.core.annotation.Experiment;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Programmatic entry point for recording experiment runs (HU-022).
 *
 * <p>Each call to {@link #run} creates a fresh {@link ExperimentContext},
 * supplies it to the given task, captures the return value and all logged
 * metrics / artefacts, and persists an {@link ExperimentRun} in the
 * configured {@link ExperimentStore}.
 *
 * <p>Usage:
 * <pre>{@code
 * ExperimentRunner runner = new ExperimentRunner();
 *
 * MyModel model = runner.run(
 *         "churn-xgboost",
 *         new String[]{"xgboost", "churn"},
 *         Map.of("lr", 0.1, "depth", 5),
 *         ctx -> {
 *             MyModel m = XGBoost.train(data, 0.1, 5);
 *             ctx.logMetric("auc", m.auc());
 *             ctx.logArtifact("confusion_matrix", m.confusionMatrix());
 *             return m;
 *         });
 * }</pre>
 *
 * <p>The CDI interceptor for {@code @Experiment} in the Quarkus adapter bridges
 * the annotation-based API to this programmatic runner, extracting method
 * parameters and injecting the {@link ExperimentContext} argument.
 */
public final class ExperimentRunner {

    private static final Logger LOG = Logger.getLogger(ExperimentRunner.class.getName());

    private final ExperimentStore store;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * Creates a runner that discovers its {@link ExperimentStore} via
     * {@link ServiceLoader}, falling back to {@link InMemoryExperimentStore}.
     */
    public ExperimentRunner() {
        this(ServiceLoader.load(ExperimentStore.class)
                .findFirst()
                .orElseGet(InMemoryExperimentStore::new));
    }

    /**
     * Creates a runner backed by the given store (useful for testing).
     */
    public ExperimentRunner(ExperimentStore store) {
        this.store = store;
    }

    // -------------------------------------------------------------------------
    // Core execution
    // -------------------------------------------------------------------------

    /**
     * Executes the given task inside a new experiment run, persists the result,
     * and returns the task's return value.
     *
     * @param <T>            return type of the task
     * @param experimentName experiment group name (maps to
     *                       {@link Experiment#name()})
     * @param tags           free-form tags
     * @param params         parameter map to record (e.g. hyper-parameters)
     * @param task           receives a fresh {@link ExperimentContext} and
     *                       returns the training result
     * @return the value returned by {@code task}
     * @throws RuntimeException re-throws any exception from the task (after
     *                          persisting a FAILED run)
     */
    public <T> T run(String experimentName,
                     String[] tags,
                     Map<String, Object> params,
                     Function<ExperimentContext, T> task) {

        String id       = UUID.randomUUID().toString();
        Instant started = Instant.now();
        ExperimentContext ctx = new ExperimentContext();

        try {
            T result = task.apply(ctx);
            Instant finished = Instant.now();
            ExperimentRun run = ExperimentRun.completed(
                    id, experimentName, tags, Map.copyOf(params), started, finished, ctx, result);
            store.save(run);
            LOG.fine(() -> "Gauss Vigil: run '" + id + "' for experiment '"
                    + experimentName + "' completed");
            return result;
        } catch (Exception e) {
            Instant finished = Instant.now();
            ExperimentRun run = ExperimentRun.failed(
                    id, experimentName, tags, Map.copyOf(params), started, finished, ctx, e);
            store.save(run);
            LOG.log(Level.WARNING, "Gauss Vigil: run '" + id + "' for experiment '"
                    + experimentName + "' failed", e);
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException(e);
        }
    }

    /**
     * Convenience overload with no params map (equivalent to passing an empty map).
     */
    public <T> T run(String experimentName,
                     String[] tags,
                     Function<ExperimentContext, T> task) {
        return run(experimentName, tags, Map.of(), task);
    }

    /**
     * Convenience overload for a single-tag, no-params run.
     */
    public <T> T run(String experimentName,
                     Function<ExperimentContext, T> task) {
        return run(experimentName, new String[0], Map.of(), task);
    }

    // -------------------------------------------------------------------------
    // Reflection-based invocation (CDI interceptor path)
    // -------------------------------------------------------------------------

    /**
     * Invokes {@code method} on {@code target} after injecting a new
     * {@link ExperimentContext} into the parameter slot that has that type,
     * then records the run.  Called by the Quarkus CDI interceptor.
     *
     * @param target   the bean instance
     * @param method   the {@code @Experiment}-annotated method
     * @param rawArgs  original argument array from the interceptor
     * @param ann      the {@code @Experiment} annotation on the method
     * @return the return value of the method invocation
     * @throws Exception propagated from the method
     */
    public Object invokeAnnotated(Object target,
                                   java.lang.reflect.Method method,
                                   Object[] rawArgs,
                                   Experiment ann) throws Exception {

        String name = ann.name().isBlank() ? method.getName() : ann.name();
        String[] tags = ann.tags();

        // Build params from non-context args
        Map<String, Object> params = new LinkedHashMap<>();
        java.lang.reflect.Parameter[] methodParams = method.getParameters();
        for (int i = 0; i < methodParams.length; i++) {
            if (!ExperimentContext.class.isAssignableFrom(methodParams[i].getType())) {
                params.put(methodParams[i].getName(), rawArgs != null ? rawArgs[i] : null);
            }
        }

        ExperimentContext ctx = new ExperimentContext();
        Object[] args = buildArgs(method, rawArgs, ctx);

        String id       = UUID.randomUUID().toString();
        Instant started = Instant.now();
        try {
            Object result   = method.invoke(target, args);
            Instant finished = Instant.now();
            store.save(ExperimentRun.completed(
                    id, name, tags, Map.copyOf(params), started, finished, ctx, result));
            return result;
        } catch (java.lang.reflect.InvocationTargetException ite) {
            Instant finished = Instant.now();
            Throwable cause = ite.getCause() != null ? ite.getCause() : ite;
            store.save(ExperimentRun.failed(
                    id, name, tags, Map.copyOf(params), started, finished, ctx, cause));
            throw (cause instanceof Exception ex) ? ex : new RuntimeException(cause);
        }
    }

    // -------------------------------------------------------------------------

    private static Object[] buildArgs(java.lang.reflect.Method method,
                                       Object[] rawArgs,
                                       ExperimentContext ctx) {
        java.lang.reflect.Parameter[] params = method.getParameters();
        Object[] args = Arrays.copyOf(rawArgs != null ? rawArgs : new Object[params.length],
                params.length);
        for (int i = 0; i < params.length; i++) {
            if (ExperimentContext.class.isAssignableFrom(params[i].getType())) {
                args[i] = ctx;
            }
        }
        return args;
    }

    // -------------------------------------------------------------------------

    /** Returns the backing store (for querying). */
    public ExperimentStore store() {
        return store;
    }
}

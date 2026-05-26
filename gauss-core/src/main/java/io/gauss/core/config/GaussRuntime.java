package io.gauss.core.config;

/** Supported JVM runtimes for Gauss applications. */
public enum GaussRuntime {

    /** Quarkus (CDI / RESTEasy Reactive). Default and primary runtime. */
    QUARKUS,

    /** Spring Boot (Spring IoC / Spring MVC). Supported from Sprint 1+. */
    SPRING
}

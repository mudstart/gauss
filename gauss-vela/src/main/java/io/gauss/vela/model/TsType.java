package io.gauss.vela.model;

/**
 * Represents a resolved TypeScript type with its nullability.
 *
 * <p>Immutable value type produced by {@link io.gauss.vela.mapper.JavaTypeMapper}.
 * Call {@link #render()} to get the final TypeScript source fragment.
 *
 * <p>Examples:
 * <pre>
 *   TsType.of("string")              → "string"
 *   TsType.of("number").orNull()     → "number | null"
 *   TsType.of("number").orUndefined()→ "number | undefined"
 *   TsType.of("User[]")              → "User[]"
 * </pre>
 */
public final class TsType {

    private final String base;
    private final boolean nullable;
    private final boolean undefinable;

    private TsType(String base, boolean nullable, boolean undefinable) {
        this.base = base;
        this.nullable = nullable;
        this.undefinable = undefinable;
    }

    public static TsType of(String base) {
        return new TsType(base, false, false);
    }

    public TsType orNull() {
        return new TsType(base, true, undefinable);
    }

    public TsType orUndefined() {
        return new TsType(base, nullable, true);
    }

    /** The raw base type without null/undefined unions (e.g. {@code "string"}, {@code "User[]"}). */
    public String base() { return base; }

    public boolean isNullable()    { return nullable; }
    public boolean isUndefinable() { return undefinable; }

    /**
     * Returns the full TypeScript type fragment including null/undefined unions.
     * E.g. {@code "string | null | undefined"}.
     */
    public String render() {
        if (!nullable && !undefinable) return base;
        StringBuilder sb = new StringBuilder(base);
        if (nullable)    sb.append(" | null");
        if (undefinable) sb.append(" | undefined");
        return sb.toString();
    }

    @Override
    public String toString() { return render(); }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TsType t)) return false;
        return base.equals(t.base) && nullable == t.nullable && undefinable == t.undefinable;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * base.hashCode() + Boolean.hashCode(nullable)) + Boolean.hashCode(undefinable);
    }
}

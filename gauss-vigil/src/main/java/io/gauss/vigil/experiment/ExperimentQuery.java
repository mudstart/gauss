package io.gauss.vigil.experiment;

import java.time.Instant;
import java.util.List;

/**
 * Fluent query descriptor for filtering and sorting experiment runs (HU-023).
 *
 * <p>Build via the static factory methods and pass to
 * {@link ExperimentQueryService#query(ExperimentQuery)}.
 *
 * <pre>{@code
 * ExperimentQuery query = ExperimentQuery.builder()
 *         .name("churn-xgboost")
 *         .tags(List.of("production"))
 *         .from(Instant.parse("2026-01-01T00:00:00Z"))
 *         .sortByMetric("auc", false)  // descending
 *         .page(0, 20)
 *         .build();
 * }</pre>
 */
public final class ExperimentQuery {

    private final String       nameFilter;
    private final List<String> tags;
    private final Instant      from;
    private final Instant      to;
    private final String       sortMetric;
    private final boolean      sortAscending;
    private final int          page;
    private final int          pageSize;

    private ExperimentQuery(Builder b) {
        this.nameFilter    = b.nameFilter;
        this.tags          = b.tags == null ? List.of() : List.copyOf(b.tags);
        this.from          = b.from;
        this.to            = b.to;
        this.sortMetric    = b.sortMetric;
        this.sortAscending = b.sortAscending;
        this.page          = b.page;
        this.pageSize      = b.pageSize;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public String       nameFilter()    { return nameFilter;    }
    public List<String> tags()          { return tags;          }
    public Instant      from()          { return from;          }
    public Instant      to()            { return to;            }
    public String       sortMetric()    { return sortMetric;    }
    public boolean      sortAscending() { return sortAscending; }
    public int          page()          { return page;          }
    public int          pageSize()      { return pageSize;      }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /** Returns a fresh {@link Builder} instance. */
    public static Builder builder() { return new Builder(); }

    /** Convenience: a query that returns all runs (no filters, page size 50). */
    public static ExperimentQuery all() { return builder().build(); }

    public static final class Builder {
        private String       nameFilter;
        private List<String> tags;
        private Instant      from;
        private Instant      to;
        private String       sortMetric;
        private boolean      sortAscending = true;
        private int          page          = 0;
        private int          pageSize      = 50;

        private Builder() {}

        /** Filter by experiment group name (exact match). */
        public Builder name(String name)            { this.nameFilter = name; return this; }

        /** Filter runs that contain all of the given tags. */
        public Builder tags(List<String> tags)       { this.tags = tags;       return this; }

        /** Only include runs that started at or after {@code from}. */
        public Builder from(Instant from)            { this.from = from;       return this; }

        /** Only include runs that finished at or before {@code to}. */
        public Builder to(Instant to)                { this.to = to;           return this; }

        /**
         * Sort results by the last-recorded value of the given metric.
         * Runs that did not log this metric are placed at the end.
         *
         * @param metric    metric name
         * @param ascending {@code true} for ascending order
         */
        public Builder sortByMetric(String metric, boolean ascending) {
            this.sortMetric    = metric;
            this.sortAscending = ascending;
            return this;
        }

        /** Zero-based page index and number of items per page. */
        public Builder page(int page, int pageSize) {
            this.page     = page;
            this.pageSize = pageSize;
            return this;
        }

        public ExperimentQuery build() { return new ExperimentQuery(this); }
    }
}

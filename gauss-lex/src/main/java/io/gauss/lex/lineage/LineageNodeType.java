package io.gauss.lex.lineage;

/** Types of nodes in the data lineage graph (HU-050). */
public enum LineageNodeType {
    /** An ML model that produced a prediction. */
    MODEL,
    /** A single prediction produced by a model. */
    PREDICTION,
    /** A feature value computed by the feature store. */
    FEATURE,
    /** A data pipeline that computed or transformed data. */
    PIPELINE,
    /** An original data source (database table, file, API). */
    DATA_SOURCE
}

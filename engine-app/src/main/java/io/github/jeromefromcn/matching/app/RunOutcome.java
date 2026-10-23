package io.github.jeromefromcn.matching.app;

enum RunOutcome {
    RUNNING,
    /** All intervals of the measurement window were written. */
    COMPLETED,
    /** The byte cap was reached; the log ends with a truncation marker and the run is invalid. */
    TRUNCATED
}

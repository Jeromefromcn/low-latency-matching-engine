package io.github.jeromefromcn.matching.app;

/**
 * Timing and size bounds of one run. The measurement window must be a whole number of log intervals,
 * so the number of intervals in the {@code .hlog} is known before the run starts.
 */
record RunPlan(long warmupNanos, long measurementNanos, long logIntervalNanos, long maxHlogBytes) {

    RunPlan {
        if (warmupNanos < 0) {
            throw new IllegalArgumentException("warm-up must not be negative");
        }
        if (logIntervalNanos <= 0 || measurementNanos <= 0) {
            throw new IllegalArgumentException("measurement window and log interval must be positive");
        }
        if (measurementNanos % logIntervalNanos != 0) {
            throw new IllegalArgumentException("measurement window must be a whole number of log intervals");
        }
        if (maxHlogBytes <= 0) {
            throw new IllegalArgumentException("maxHlogBytes must be positive");
        }
    }

    long intervalCount() {
        return measurementNanos / logIntervalNanos;
    }
}

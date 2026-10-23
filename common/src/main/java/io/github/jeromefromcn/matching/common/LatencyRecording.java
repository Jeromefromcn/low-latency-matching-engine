package io.github.jeromefromcn.matching.common;

/** Histogram range shared by every latency recorder (docs/benchmark-methodology.md §5). */
public final class LatencyRecording {

    /** 10 s. Recorders are fixed-size: an auto-resizing histogram would allocate on the hot path. */
    public static final long HIGHEST_TRACKABLE_LATENCY_NANOS = 10_000_000_000L;

    public static final int SIGNIFICANT_DIGITS = 3;

    private LatencyRecording() {}
}

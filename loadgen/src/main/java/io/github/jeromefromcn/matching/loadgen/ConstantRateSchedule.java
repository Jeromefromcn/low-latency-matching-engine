package io.github.jeromefromcn.matching.loadgen;

/**
 * Open-model schedule: message {@code i} is due at {@code start + i / rate}, independent of when
 * earlier messages were actually sent or answered. Latency is measured from this intended time, so
 * a stalled generator is charged to latency instead of hidden (coordinated omission).
 */
public final class ConstantRateSchedule {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final long startNanos;
    private final long ratePerSecond;

    public ConstantRateSchedule(long startNanos, long ratePerSecond) {
        if (ratePerSecond <= 0) {
            throw new IllegalArgumentException("ratePerSecond must be positive: " + ratePerSecond);
        }
        this.startNanos = startNanos;
        this.ratePerSecond = ratePerSecond;
    }

    /** Exact integer arithmetic: whole seconds plus the remainder, so there is no drift or overflow. */
    public long intendedSendTimeNanos(long sequence) {
        long wholeSeconds = sequence / ratePerSecond;
        long remainder = sequence % ratePerSecond;
        return startNanos + wholeSeconds * NANOS_PER_SECOND + remainder * NANOS_PER_SECOND / ratePerSecond;
    }
}

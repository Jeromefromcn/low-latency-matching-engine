package io.github.jeromefromcn.matching.app;

import static io.github.jeromefromcn.matching.common.LatencyRecording.HIGHEST_TRACKABLE_LATENCY_NANOS;
import static io.github.jeromefromcn.matching.common.LatencyRecording.SIGNIFICANT_DIGITS;

import java.io.BufferedOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.HistogramLogWriter;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.NanoClock;

/**
 * Measurement plane (ADR 0001): snapshots every recorder once per log interval and writes the
 * intervals to {@code latency.hlog} (values in nanoseconds). At the end of the measurement window it
 * writes one {@code <tag>.hgrm} percentile distribution per tag (values in microseconds).
 *
 * <p>Samples recorded during warm-up are discarded. The {@code .hlog} is bounded by the run plan:
 * after each interval the bytes written are checked against the cap, and exceeding it ends the run as
 * {@link RunOutcome#TRUNCATED} with a marker line; the log may overshoot the cap by one interval.
 *
 * <p>Runs on the housekeeping thread, off the hot path; allocation here does not touch hot-path
 * threads.
 */
final class HousekeepingAgent implements Agent {

    private static final double NANOS_PER_MICRO = 1_000.0;

    private final NanoClock clock;
    private final RunPlan plan;
    private final List<TaggedRecorder> recorders;
    private final Path outputDir;
    private final long warmupEndNanos;
    private final Histogram[] intervals;
    private final Histogram[] totals;
    private final CountingOutputStream hlogBytes;
    private final HistogramLogWriter hlog;

    private boolean measuring;
    private long intervalsWritten;
    private volatile RunOutcome outcome = RunOutcome.RUNNING;

    HousekeepingAgent(NanoClock clock, RunPlan plan, List<TaggedRecorder> recorders, Path outputDir)
            throws IOException {
        this.clock = clock;
        this.plan = plan;
        this.recorders = List.copyOf(recorders);
        this.outputDir = outputDir;
        this.warmupEndNanos = clock.nanoTime() + plan.warmupNanos();
        this.intervals = new Histogram[recorders.size()];
        this.totals = new Histogram[recorders.size()];
        for (int i = 0; i < totals.length; i++) {
            totals[i] = new Histogram(1, HIGHEST_TRACKABLE_LATENCY_NANOS, SIGNIFICANT_DIGITS);
        }
        this.hlogBytes = new CountingOutputStream(
                new BufferedOutputStream(Files.newOutputStream(outputDir.resolve("latency.hlog"))));
        this.hlog = new HistogramLogWriter(hlogBytes);
    }

    RunOutcome outcome() {
        return outcome;
    }

    @Override
    public int doWork() {
        if (outcome != RunOutcome.RUNNING) {
            return 0;
        }
        long now = clock.nanoTime();
        if (!measuring) {
            if (now < warmupEndNanos) {
                return 0;
            }
            startMeasuring();
            return 1;
        }
        if (now < warmupEndNanos + (intervalsWritten + 1) * plan.logIntervalNanos()) {
            return 0;
        }
        writeInterval();
        if (hlogBytes.count > plan.maxHlogBytes()) {
            hlog.outputComment("TRUNCATED: " + hlogBytes.count + " bytes exceeds cap of " + plan.maxHlogBytes()
                    + " after " + intervalsWritten + " of " + plan.intervalCount() + " intervals");
            finish(RunOutcome.TRUNCATED);
        } else if (intervalsWritten == plan.intervalCount()) {
            writePercentileDistributions();
            finish(RunOutcome.COMPLETED);
        }
        return 1;
    }

    private void startMeasuring() {
        for (int i = 0; i < intervals.length; i++) {
            intervals[i] = recorders.get(i).recorder().getIntervalHistogram(intervals[i]); // discard warm-up
        }
        hlog.outputLogFormatVersion();
        long startMillis = System.currentTimeMillis();
        hlog.outputStartTime(startMillis);
        hlog.setBaseTime(startMillis);
        hlog.outputLegend();
        measuring = true;
    }

    private void writeInterval() {
        for (int i = 0; i < intervals.length; i++) {
            TaggedRecorder tagged = recorders.get(i);
            intervals[i] = tagged.recorder().getIntervalHistogram(intervals[i]);
            intervals[i].setTag(tagged.tag());
            totals[i].add(intervals[i]);
            hlog.outputIntervalHistogram(intervals[i]);
        }
        intervalsWritten++;
    }

    private void writePercentileDistributions() {
        for (int i = 0; i < totals.length; i++) {
            Path file = outputDir.resolve(recorders.get(i).tag() + ".hgrm");
            try (PrintStream out = new PrintStream(Files.newOutputStream(file), false, StandardCharsets.UTF_8)) {
                totals[i].outputPercentileDistribution(out, NANOS_PER_MICRO);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private void finish(RunOutcome result) {
        hlog.close();
        outcome = result;
    }

    @Override
    public void onClose() {
        if (outcome == RunOutcome.RUNNING) {
            hlog.close();
        }
    }

    @Override
    public String roleName() {
        return "housekeeping";
    }

    /** Counts bytes on their way to the file, so the cap applies to what is actually written. */
    private static final class CountingOutputStream extends FilterOutputStream {
        private long count;

        CountingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            count++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            count += len;
        }
    }
}

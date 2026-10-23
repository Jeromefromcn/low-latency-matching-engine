package io.github.jeromefromcn.matching.app;

import static io.github.jeromefromcn.matching.common.LatencyRecording.HIGHEST_TRACKABLE_LATENCY_NANOS;
import static io.github.jeromefromcn.matching.common.LatencyRecording.SIGNIFICANT_DIGITS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.HdrHistogram.EncodableHistogram;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.HistogramLogReader;
import org.HdrHistogram.SingleWriterRecorder;
import org.agrona.concurrent.CachedNanoClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HousekeepingAgentTest {

    private static final long SECOND = 1_000_000_000L;
    private static final long NO_CAP = Long.MAX_VALUE;

    @TempDir
    Path dir;

    private final CachedNanoClock clock = new CachedNanoClock();
    private final SingleWriterRecorder pipeline = recorder();
    private final SingleWriterRecorder engine = recorder();

    @Test
    void writesOneIntervalPerTagForEachLogIntervalOfTheMeasurementWindow() throws IOException {
        // 2 s warm-up, 3 s measured, 1 s interval -> exactly 3 intervals per tag.
        HousekeepingAgent agent = agent(new RunPlan(2 * SECOND, 3 * SECOND, SECOND, NO_CAP));

        for (long t = 0; t <= 6 * SECOND; t += SECOND / 4) {
            clock.update(t);
            pipeline.recordValue(1_000);
            engine.recordValue(100);
            agent.doWork();
        }

        assertEquals(RunOutcome.COMPLETED, agent.outcome());
        List<Histogram> intervals = readIntervals();
        assertEquals(6, intervals.size());
        assertEquals(
                3, intervals.stream().filter(h -> "pipeline".equals(h.getTag())).count());
        assertEquals(
                3, intervals.stream().filter(h -> "engine".equals(h.getTag())).count());
    }

    @Test
    void samplesRecordedDuringWarmUpAreDiscarded() throws IOException {
        HousekeepingAgent agent = agent(new RunPlan(SECOND, SECOND, SECOND, NO_CAP));

        clock.update(0);
        pipeline.recordValue(999_000);
        agent.doWork();
        clock.update(SECOND);
        agent.doWork(); // warm-up ends here
        pipeline.recordValue(5_000);
        pipeline.recordValue(5_000);
        clock.update(2 * SECOND);
        agent.doWork();

        assertEquals(RunOutcome.COMPLETED, agent.outcome());
        Histogram measured = readIntervals().stream()
                .filter(h -> "pipeline".equals(h.getTag()))
                .findFirst()
                .orElseThrow();
        assertEquals(2, measured.getTotalCount());
        assertTrue(measured.valuesAreEquivalent(5_000, measured.getMaxValue()));
    }

    @Test
    void writesAPercentileDistributionPerTagCoveringTheWholeMeasurementWindow() throws IOException {
        HousekeepingAgent agent = agent(new RunPlan(0, 2 * SECOND, SECOND, NO_CAP));

        clock.update(0);
        agent.doWork();
        engine.recordValue(100);
        clock.update(SECOND);
        agent.doWork();
        engine.recordValue(100);
        engine.recordValue(100);
        clock.update(2 * SECOND);
        agent.doWork();

        String hgrm = Files.readString(dir.resolve("engine.hgrm"));
        assertTrue(hgrm.matches("(?s).*Total count\\s*=\\s*3\\b.*"), hgrm);
    }

    @Test
    void exceedingTheByteCapWritesATruncationMarkerAndStopsRecording() throws IOException {
        HousekeepingAgent agent = agent(new RunPlan(0, 10 * SECOND, SECOND, 1));

        for (long t = 0; t <= 10 * SECOND; t += SECOND) {
            clock.update(t);
            pipeline.recordValue(1_000);
            agent.doWork();
        }

        assertEquals(RunOutcome.TRUNCATED, agent.outcome());
        String log = Files.readString(dir.resolve("latency.hlog"));
        assertTrue(log.contains("#TRUNCATED"), log);
        // Stopped after the first interval (both tags) instead of writing all ten.
        assertEquals(2, readIntervals().size());
        assertFalse(Files.exists(dir.resolve("pipeline.hgrm")));
    }

    private HousekeepingAgent agent(RunPlan plan) throws IOException {
        clock.update(0);
        return new HousekeepingAgent(
                clock,
                plan,
                List.of(new TaggedRecorder("pipeline", pipeline), new TaggedRecorder("engine", engine)),
                dir);
    }

    private List<Histogram> readIntervals() throws FileNotFoundException {
        HistogramLogReader reader =
                new HistogramLogReader(dir.resolve("latency.hlog").toFile());
        List<Histogram> intervals = new ArrayList<>();
        EncodableHistogram histogram;
        while ((histogram = reader.nextIntervalHistogram()) != null) {
            intervals.add((Histogram) histogram);
        }
        return intervals;
    }

    private static SingleWriterRecorder recorder() {
        return new SingleWriterRecorder(1, HIGHEST_TRACKABLE_LATENCY_NANOS, SIGNIFICANT_DIGITS);
    }
}

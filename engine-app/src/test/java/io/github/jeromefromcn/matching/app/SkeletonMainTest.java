package io.github.jeromefromcn.matching.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.HdrHistogram.EncodableHistogram;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.HistogramLogReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** End to end with real threads, ring buffers and clock: loadgen -> stub engine -> loadgen. */
class SkeletonMainTest {

    @TempDir
    Path dir;

    @Test
    void devRunProducesAReadableHlogWithTheConfiguredNumberOfIntervals() throws IOException {
        int exitCode = SkeletonMain.run(
                "profile=dev",
                "outputDir=" + dir,
                "rate=2000",
                "warmupSeconds=1",
                "durationSeconds=1",
                "intervalMillis=250");

        assertEquals(0, exitCode);
        Map<String, Integer> intervalsPerTag = new HashMap<>();
        Map<String, Long> samplesPerTag = new HashMap<>();
        HistogramLogReader reader = reader();
        EncodableHistogram interval;
        while ((interval = reader.nextIntervalHistogram()) != null) {
            intervalsPerTag.merge(interval.getTag(), 1, Integer::sum);
            samplesPerTag.merge(interval.getTag(), ((Histogram) interval).getTotalCount(), Long::sum);
        }
        assertEquals(Map.of("pipeline", 4, "engine", 4), intervalsPerTag);
        // 2000 msg/s for 1 s; allow for a slow CI runner, but samples must be there.
        assertTrue(samplesPerTag.get("pipeline") > 1_000, samplesPerTag.toString());
        assertTrue(samplesPerTag.get("engine") > 1_000, samplesPerTag.toString());
        assertTrue(Files.exists(dir.resolve("pipeline.hgrm")));
        assertTrue(Files.exists(dir.resolve("engine.hgrm")));
    }

    @Test
    void runExceedingTheByteCapExitsNonZeroWithTheTruncationMarker() throws IOException {
        int exitCode = SkeletonMain.run(
                "profile=dev",
                "outputDir=" + dir,
                "rate=2000",
                "warmupSeconds=0",
                "durationSeconds=2",
                "intervalMillis=250",
                "maxHlogBytes=1");

        assertEquals(2, exitCode);
        assertTrue(Files.readString(dir.resolve("latency.hlog")).contains("#TRUNCATED"));
    }

    @Test
    void benchProfileExitsWithAConfigurationErrorBeforeStartingAnyThread() {
        assertEquals(1, SkeletonMain.run("profile=bench", "outputDir=" + dir));
        assertFalse(Files.exists(dir.resolve("latency.hlog")));
    }

    private HistogramLogReader reader() throws FileNotFoundException {
        return new HistogramLogReader(dir.resolve("latency.hlog").toFile());
    }
}

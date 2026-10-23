package io.github.jeromefromcn.matching.app;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.agrona.BitUtil;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SleepingIdleStrategy;
import org.agrona.concurrent.YieldingIdleStrategy;

/**
 * Walking-skeleton configuration from {@code key=value} arguments. A profile supplies the defaults and
 * any other argument overrides them:
 *
 * <ul>
 *   <li>{@code dev}: sleeping idle strategy, no pinning, short run;
 *   <li>{@code bench}: busy-spin and the core allocation in CLAUDE.md, methodology-length run.
 * </ul>
 *
 * <p>CPU pinning is configurable but not implemented until ROADMAP 3.1; requesting it fails fast
 * instead of running unpinned while appearing pinned. {@code -1} means "not pinned".
 */
record SkeletonConfig(
        Path outputDir,
        long ratePerSecond,
        RunPlan runPlan,
        int ringCapacity,
        String idle,
        int cpuLoadgen,
        int cpuEngine,
        int cpuHousekeeping) {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final long NANOS_PER_MILLI = 1_000_000L;

    private static final Map<String, String> COMMON_DEFAULTS = Map.of(
            "rate", "10000",
            "intervalMillis", "1000",
            "maxDurationSeconds", "3600",
            "maxHlogBytes", String.valueOf(64L * 1024 * 1024),
            "ringCapacity", String.valueOf(1024 * 1024));

    private static final Map<String, Map<String, String>> PROFILES = Map.of(
            "dev",
                    Map.of(
                            "idle", "sleeping",
                            "warmupSeconds", "10",
                            "durationSeconds", "60",
                            "cpu.loadgen", "-1",
                            "cpu.engine", "-1",
                            "cpu.housekeeping", "-1"),
            "bench",
                    Map.of(
                            "idle", "busy-spin",
                            "warmupSeconds", "60",
                            "durationSeconds", "600",
                            "cpu.loadgen", "1",
                            "cpu.engine", "2",
                            "cpu.housekeeping", "0"));

    private static final Set<String> IDLE_STRATEGIES = Set.of("sleeping", "yielding", "backoff", "busy-spin");

    static SkeletonConfig parse(String... args) {
        Map<String, String> overrides = new LinkedHashMap<>();
        for (String arg : args) {
            int eq = arg.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException("expected key=value, got: " + arg);
            }
            overrides.put(arg.substring(0, eq), arg.substring(eq + 1));
        }
        String profile = overrides.getOrDefault("profile", "dev");
        Map<String, String> profileDefaults = PROFILES.get(profile);
        if (profileDefaults == null) {
            throw new IllegalArgumentException(
                    "unknown profile: " + profile + " (expected one of " + PROFILES.keySet() + ")");
        }

        Map<String, String> values = new LinkedHashMap<>(COMMON_DEFAULTS);
        values.putAll(profileDefaults);
        values.put("profile", profile);
        values.put("outputDir", "");
        for (Map.Entry<String, String> e : overrides.entrySet()) {
            if (!values.containsKey(e.getKey())) {
                throw new IllegalArgumentException("unknown key: " + e.getKey() + " (known: " + values.keySet() + ")");
            }
            values.put(e.getKey(), e.getValue());
        }
        return fromValues(values);
    }

    private static SkeletonConfig fromValues(Map<String, String> values) {
        String outputDir = values.get("outputDir");
        if (outputDir.isEmpty()) {
            throw new IllegalArgumentException("outputDir is required");
        }
        String idle = values.get("idle");
        if (!IDLE_STRATEGIES.contains(idle)) {
            throw new IllegalArgumentException(
                    "unknown idle strategy: " + idle + " (expected one of " + IDLE_STRATEGIES + ")");
        }
        long warmupSeconds = Long.parseLong(values.get("warmupSeconds"));
        long durationSeconds = Long.parseLong(values.get("durationSeconds"));
        long maxDurationSeconds = Long.parseLong(values.get("maxDurationSeconds"));
        if (warmupSeconds + durationSeconds > maxDurationSeconds) {
            throw new IllegalArgumentException("run of " + (warmupSeconds + durationSeconds)
                    + " s (warm-up + measurement) exceeds maxDurationSeconds=" + maxDurationSeconds);
        }
        int ringCapacity = Integer.parseInt(values.get("ringCapacity"));
        if (!BitUtil.isPowerOfTwo(ringCapacity)) {
            throw new IllegalArgumentException("ringCapacity must be a power of two: " + ringCapacity);
        }
        RunPlan plan = new RunPlan(
                warmupSeconds * NANOS_PER_SECOND,
                durationSeconds * NANOS_PER_SECOND,
                Long.parseLong(values.get("intervalMillis")) * NANOS_PER_MILLI,
                Long.parseLong(values.get("maxHlogBytes")));
        return new SkeletonConfig(
                Path.of(outputDir),
                Long.parseLong(values.get("rate")),
                plan,
                ringCapacity,
                idle,
                Integer.parseInt(values.get("cpu.loadgen")),
                Integer.parseInt(values.get("cpu.engine")),
                Integer.parseInt(values.get("cpu.housekeeping")));
    }

    /** One instance per agent runner: idle strategies keep per-thread state. */
    IdleStrategy newHotPathIdleStrategy() {
        return switch (idle) {
            case "sleeping" -> new SleepingIdleStrategy();
            case "yielding" -> new YieldingIdleStrategy();
            case "backoff" -> new BackoffIdleStrategy();
            case "busy-spin" -> new BusySpinIdleStrategy();
            default -> throw new IllegalStateException("validated in parse: " + idle);
        };
    }

    void checkAffinitySupported() {
        if (cpuLoadgen >= 0 || cpuEngine >= 0 || cpuHousekeeping >= 0) {
            throw new IllegalStateException("CPU pinning requested (loadgen=" + cpuLoadgen + ", engine=" + cpuEngine
                    + ", housekeeping=" + cpuHousekeeping + ") but thread pinning is not implemented until ROADMAP 3.1;"
                    + " set cpu.loadgen=-1 cpu.engine=-1 cpu.housekeeping=-1 to run unpinned");
        }
    }
}

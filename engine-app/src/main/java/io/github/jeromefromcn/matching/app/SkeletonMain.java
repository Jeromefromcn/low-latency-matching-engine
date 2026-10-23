package io.github.jeromefromcn.matching.app;

import static io.github.jeromefromcn.matching.common.LatencyRecording.HIGHEST_TRACKABLE_LATENCY_NANOS;
import static io.github.jeromefromcn.matching.common.LatencyRecording.SIGNIFICANT_DIGITS;

import io.github.jeromefromcn.matching.loadgen.ConstantRateSchedule;
import io.github.jeromefromcn.matching.loadgen.LoadGeneratorAgent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.HdrHistogram.SingleWriterRecorder;
import org.agrona.CloseHelper;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.NanoClock;
import org.agrona.concurrent.SleepingMillisIdleStrategy;
import org.agrona.concurrent.SystemNanoClock;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RingBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;

/**
 * Walking skeleton (ROADMAP 0.4): loadgen -> inbound ring -> stub engine -> outbound ring -> loadgen,
 * with the housekeeping thread writing the {@code .hlog}. All three agents run in one process for now.
 *
 * <p>Exit codes: 0 completed, 1 configuration error, 2 truncated by the {@code .hlog} byte cap.
 */
public final class SkeletonMain {

    static final int EXIT_COMPLETED = 0;
    static final int EXIT_CONFIG_ERROR = 1;
    static final int EXIT_TRUNCATED = 2;

    /** Extra time allowed after the planned run before it is treated as hung. */
    private static final long SHUTDOWN_GRACE_NANOS = TimeUnit.SECONDS.toNanos(10);

    private SkeletonMain() {}

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String... args) {
        SkeletonConfig config;
        try {
            config = SkeletonConfig.parse(args);
            config.checkAffinitySupported();
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.err.println("configuration error: " + e.getMessage());
            return EXIT_CONFIG_ERROR;
        }
        RunOutcome outcome = execute(config);
        System.out.println(
                "outcome=" + outcome + " outputDir=" + config.outputDir().toAbsolutePath());
        return outcome == RunOutcome.COMPLETED ? EXIT_COMPLETED : EXIT_TRUNCATED;
    }

    private static RunOutcome execute(SkeletonConfig config) {
        NanoClock clock = SystemNanoClock.INSTANCE;
        RingBuffer inbound = ringBuffer(config.ringCapacity());
        RingBuffer outbound = ringBuffer(config.ringCapacity());
        SingleWriterRecorder pipelineLatency = recorder();
        SingleWriterRecorder engineLatency = recorder();
        AtomicReference<Throwable> firstError = new AtomicReference<>();

        HousekeepingAgent housekeeping;
        try {
            Files.createDirectories(config.outputDir());
            housekeeping = new HousekeepingAgent(
                    clock,
                    config.runPlan(),
                    List.of(
                            new TaggedRecorder("pipeline", pipelineLatency),
                            new TaggedRecorder("engine", engineLatency)),
                    config.outputDir());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        AgentRunner housekeepingRunner = runner(new SleepingMillisIdleStrategy(1), housekeeping, firstError);
        AgentRunner engineRunner = runner(
                config.newHotPathIdleStrategy(),
                new StubEngineAgent(clock, inbound, outbound, engineLatency),
                firstError);
        AgentRunner loadgenRunner = runner(
                config.newHotPathIdleStrategy(),
                new LoadGeneratorAgent(
                        clock,
                        new ConstantRateSchedule(clock.nanoTime(), config.ratePerSecond()),
                        inbound,
                        outbound,
                        pipelineLatency),
                firstError);
        try {
            AgentRunner.startOnThread(housekeepingRunner);
            AgentRunner.startOnThread(engineRunner);
            AgentRunner.startOnThread(loadgenRunner);
            return awaitOutcome(clock, config.runPlan(), housekeeping, firstError);
        } finally {
            CloseHelper.closeAll(loadgenRunner, engineRunner, housekeepingRunner);
        }
    }

    private static RunOutcome awaitOutcome(
            NanoClock clock, RunPlan plan, HousekeepingAgent housekeeping, AtomicReference<Throwable> firstError) {
        long deadline = clock.nanoTime() + plan.warmupNanos() + plan.measurementNanos() + SHUTDOWN_GRACE_NANOS;
        while (housekeeping.outcome() == RunOutcome.RUNNING) {
            Throwable error = firstError.get();
            if (error != null) {
                throw new IllegalStateException("agent failed", error);
            }
            if (clock.nanoTime() > deadline) {
                throw new IllegalStateException("run did not finish within the planned duration plus grace period");
            }
            sleepMillis(10);
        }
        return housekeeping.outcome();
    }

    private static AgentRunner runner(IdleStrategy idle, Agent agent, AtomicReference<Throwable> firstError) {
        return new AgentRunner(idle, e -> firstError.compareAndSet(null, e), null, agent);
    }

    private static RingBuffer ringBuffer(int capacity) {
        return new OneToOneRingBuffer(
                new UnsafeBuffer(ByteBuffer.allocateDirect(capacity + RingBufferDescriptor.TRAILER_LENGTH)));
    }

    private static SingleWriterRecorder recorder() {
        return new SingleWriterRecorder(1, HIGHEST_TRACKABLE_LATENCY_NANOS, SIGNIFICANT_DIGITS);
    }

    private static void sleepMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for the run", e);
        }
    }
}

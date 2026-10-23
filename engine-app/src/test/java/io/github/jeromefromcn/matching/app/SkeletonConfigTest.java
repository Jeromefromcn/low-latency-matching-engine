package io.github.jeromefromcn.matching.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.SleepingIdleStrategy;
import org.junit.jupiter.api.Test;

class SkeletonConfigTest {

    @Test
    void devProfileUsesANonSpinningIdleStrategyAndNoPinning() {
        SkeletonConfig config = SkeletonConfig.parse("profile=dev", "outputDir=out");

        assertInstanceOf(SleepingIdleStrategy.class, config.newHotPathIdleStrategy());
        config.checkAffinitySupported(); // does not throw
    }

    @Test
    void benchProfileBusySpinsButFailsFastBecausePinningIsNotImplementedYet() {
        SkeletonConfig config = SkeletonConfig.parse("profile=bench", "outputDir=out");

        assertInstanceOf(BusySpinIdleStrategy.class, config.newHotPathIdleStrategy());
        IllegalStateException e = assertThrows(IllegalStateException.class, config::checkAffinitySupported);
        assertTrue(e.getMessage().contains("engine=2"), e.getMessage());
    }

    @Test
    void anyRequestedPinningFailsFastEvenInTheDevProfile() {
        SkeletonConfig config = SkeletonConfig.parse("profile=dev", "outputDir=out", "cpu.engine=3");

        assertThrows(IllegalStateException.class, config::checkAffinitySupported);
    }

    @Test
    void overridesApplyOnTopOfTheProfile() {
        SkeletonConfig config = SkeletonConfig.parse(
                "profile=dev", "outputDir=out", "rate=20000", "warmupSeconds=5", "durationSeconds=30");

        assertEquals(20_000, config.ratePerSecond());
        assertEquals(30_000_000_000L, config.runPlan().measurementNanos());
        assertEquals(5_000_000_000L, config.runPlan().warmupNanos());
    }

    @Test
    void rejectsUnknownKeysSoThatTyposAreNotSilentlyIgnored() {
        assertThrows(IllegalArgumentException.class, () -> SkeletonConfig.parse("outputDir=out", "rat=20000"));
    }

    @Test
    void rejectsUnknownIdleStrategy() {
        assertThrows(IllegalArgumentException.class, () -> SkeletonConfig.parse("outputDir=out", "idle=nap"));
    }

    @Test
    void rejectsARunLongerThanTheDurationCap() {
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> SkeletonConfig.parse(
                        "outputDir=out", "warmupSeconds=60", "durationSeconds=600", "maxDurationSeconds=600"));
        assertTrue(e.getMessage().contains("660"), e.getMessage());
    }

    @Test
    void requiresAnOutputDirectory() {
        assertThrows(IllegalArgumentException.class, () -> SkeletonConfig.parse("profile=dev"));
    }
}

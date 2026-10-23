package io.github.jeromefromcn.matching.loadgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ConstantRateScheduleTest {

    @Test
    void messagesAreSpacedByTheRateFromTheStartTime() {
        ConstantRateSchedule schedule = new ConstantRateSchedule(500, 1_000);

        assertEquals(500, schedule.intendedSendTimeNanos(0));
        assertEquals(1_000_500, schedule.intendedSendTimeNanos(1));
        assertEquals(3_000_500, schedule.intendedSendTimeNanos(3));
    }

    @Test
    void scheduleDoesNotDriftWhenTheIntervalIsNotAWholeNumberOfNanos() {
        // 3 msgs/s: interval is 333,333,333.33 ns. Accumulating a rounded interval would put
        // message 3 at 999,999,999; the schedule must land exactly on one second.
        ConstantRateSchedule schedule = new ConstantRateSchedule(0, 3);

        assertEquals(333_333_333, schedule.intendedSendTimeNanos(1));
        assertEquals(1_000_000_000, schedule.intendedSendTimeNanos(3));
        assertEquals(3_000_000_000_000L, schedule.intendedSendTimeNanos(9_000));
    }

    @Test
    void rejectsNonPositiveRate() {
        assertThrows(IllegalArgumentException.class, () -> new ConstantRateSchedule(0, 0));
    }
}

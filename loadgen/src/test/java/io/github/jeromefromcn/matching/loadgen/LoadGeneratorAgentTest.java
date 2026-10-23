package io.github.jeromefromcn.matching.loadgen;

import static io.github.jeromefromcn.matching.common.LatencyRecording.HIGHEST_TRACKABLE_LATENCY_NANOS;
import static io.github.jeromefromcn.matching.common.LatencyRecording.SIGNIFICANT_DIGITS;
import static io.github.jeromefromcn.matching.common.SkeletonProtocol.ACK_MSG_TYPE_ID;
import static io.github.jeromefromcn.matching.common.SkeletonProtocol.NEW_ORDER_MSG_TYPE_ID;
import static io.github.jeromefromcn.matching.common.SkeletonProtocol.SEQUENCE_OFFSET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.SingleWriterRecorder;
import org.agrona.concurrent.CachedNanoClock;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RingBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;
import org.junit.jupiter.api.Test;

class LoadGeneratorAgentTest {

    private static final long MILLI = 1_000_000L;

    private final CachedNanoClock clock = new CachedNanoClock();
    private final SingleWriterRecorder recorder =
            new SingleWriterRecorder(1, HIGHEST_TRACKABLE_LATENCY_NANOS, SIGNIFICANT_DIGITS);

    @Test
    void stallOfTheGeneratorIsChargedToLatencyFromIntendedSendTime() {
        RingBuffer inbound = ringBuffer(64 * 1024);
        RingBuffer outbound = ringBuffer(64 * 1024);
        clock.update(0);
        LoadGeneratorAgent agent =
                new LoadGeneratorAgent(clock, new ConstantRateSchedule(0, 1_000), inbound, outbound, recorder);

        // The generator wakes 5 ms late: messages due at 0, 1, ..., 5 ms are all sent now.
        clock.update(5 * MILLI);
        agent.doWork();
        assertEquals(6, echo(inbound, outbound));
        agent.doWork();

        Histogram latency = recorder.getIntervalHistogram();
        assertEquals(6, latency.getTotalCount());
        assertTrue(latency.valuesAreEquivalent(5 * MILLI, latency.getMaxValue()), "max " + latency.getMaxValue());
        assertEquals(0, latency.getMinValue());
        assertEquals(1, latency.getCountAtValue(3 * MILLI));
    }

    @Test
    void messagesRefusedByAFullRingAreRetriedAndKeepTheirIntendedSendTime() {
        // 128-byte ring: each record is 8 bytes of header + 16 bytes of payload (24, 8-aligned),
        // so five messages fit.
        RingBuffer inbound = ringBuffer(128);
        RingBuffer outbound = ringBuffer(64 * 1024);
        clock.update(0);
        LoadGeneratorAgent agent =
                new LoadGeneratorAgent(clock, new ConstantRateSchedule(0, 1_000), inbound, outbound, recorder);

        // Due at 5 ms: messages 0..5, but only 0..4 fit.
        clock.update(5 * MILLI);
        agent.doWork();
        assertEquals(5, echo(inbound, outbound));

        // At 6 ms: acks for 0..4 are read; 5 (refused earlier) and 6 are sent.
        clock.update(6 * MILLI);
        agent.doWork();
        assertEquals(2, echo(inbound, outbound));
        agent.doWork();

        Histogram latency = recorder.getIntervalHistogram();
        assertEquals(7, latency.getTotalCount());
        // Message 0: due at 0, acknowledged at 6 ms. Message 5: due at 5 ms, refused, sent and
        // acknowledged at 6 ms -> 1 ms, not 0. Message 6: due and acknowledged at 6 ms -> 0.
        assertTrue(latency.valuesAreEquivalent(6 * MILLI, latency.getMaxValue()), "max " + latency.getMaxValue());
        assertEquals(1, latency.getCountAtValue(1 * MILLI));
        assertEquals(1, latency.getCountAtValue(0));
    }

    /**
     * Stands in for the engine: acknowledges every new order with the same payload. Reads until the
     * ring is empty, because one {@code read} stops at the end of the buffer when the ring wraps.
     */
    private static int echo(RingBuffer inbound, RingBuffer outbound) {
        int count = 0;
        while (inbound.consumerPosition() < inbound.producerPosition()) {
            count += inbound.read((msgTypeId, buffer, index, length) -> {
                assertEquals(NEW_ORDER_MSG_TYPE_ID, msgTypeId);
                assertTrue(buffer.getLong(index + SEQUENCE_OFFSET) >= 0);
                assertTrue(outbound.write(ACK_MSG_TYPE_ID, buffer, index, length));
            });
        }
        return count;
    }

    private static RingBuffer ringBuffer(int capacity) {
        return new OneToOneRingBuffer(
                new UnsafeBuffer(ByteBuffer.allocateDirect(capacity + RingBufferDescriptor.TRAILER_LENGTH)));
    }
}

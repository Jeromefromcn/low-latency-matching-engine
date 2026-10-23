package io.github.jeromefromcn.matching.app;

import static io.github.jeromefromcn.matching.common.LatencyRecording.HIGHEST_TRACKABLE_LATENCY_NANOS;
import static io.github.jeromefromcn.matching.common.LatencyRecording.SIGNIFICANT_DIGITS;
import static io.github.jeromefromcn.matching.common.SkeletonProtocol.ACK_MSG_TYPE_ID;
import static io.github.jeromefromcn.matching.common.SkeletonProtocol.INTENDED_SEND_TIME_OFFSET;
import static io.github.jeromefromcn.matching.common.SkeletonProtocol.MESSAGE_LENGTH;
import static io.github.jeromefromcn.matching.common.SkeletonProtocol.NEW_ORDER_MSG_TYPE_ID;
import static io.github.jeromefromcn.matching.common.SkeletonProtocol.SEQUENCE_OFFSET;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.SingleWriterRecorder;
import org.agrona.concurrent.NanoClock;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RingBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;
import org.junit.jupiter.api.Test;

class StubEngineAgentTest {

    /** Each reading is 250 ns after the previous one. */
    private final NanoClock steppingClock = new NanoClock() {
        private long now;

        @Override
        public long nanoTime() {
            return now += 250;
        }
    };

    private final SingleWriterRecorder recorder =
            new SingleWriterRecorder(1, HIGHEST_TRACKABLE_LATENCY_NANOS, SIGNIFICANT_DIGITS);

    @Test
    void acknowledgesEveryNewOrderWithItsSequenceAndIntendedSendTime() {
        RingBuffer inbound = ringBuffer();
        RingBuffer outbound = ringBuffer();
        newOrder(inbound, 7, 1_000);
        newOrder(inbound, 8, 2_000);

        int work = new StubEngineAgent(steppingClock, inbound, outbound, recorder).doWork();

        assertEquals(2, work);
        List<long[]> acks = new ArrayList<>();
        outbound.read((msgTypeId, buffer, index, length) -> {
            assertEquals(ACK_MSG_TYPE_ID, msgTypeId);
            assertEquals(MESSAGE_LENGTH, length);
            acks.add(new long[] {
                buffer.getLong(index + SEQUENCE_OFFSET), buffer.getLong(index + INTENDED_SEND_TIME_OFFSET)
            });
        });
        assertEquals(2, acks.size());
        assertEquals(List.of(7L, 1_000L), List.of(acks.get(0)[0], acks.get(0)[1]));
        assertEquals(List.of(8L, 2_000L), List.of(acks.get(1)[0], acks.get(1)[1]));
    }

    @Test
    void recordsOneEngineLatencySampleFromReadToAckWritten() {
        RingBuffer inbound = ringBuffer();
        newOrder(inbound, 1, 0);
        newOrder(inbound, 2, 0);
        newOrder(inbound, 3, 0);

        new StubEngineAgent(steppingClock, inbound, ringBuffer(), recorder).doWork();

        Histogram latency = recorder.getIntervalHistogram();
        assertEquals(3, latency.getTotalCount());
        // One clock reading when the message is read, one after the ack is written: 250 ns apart.
        assertEquals(3, latency.getCountAtValue(250));
    }

    private static void newOrder(RingBuffer inbound, long sequence, long intendedSendTime) {
        UnsafeBuffer message = new UnsafeBuffer(new byte[MESSAGE_LENGTH]);
        message.putLong(SEQUENCE_OFFSET, sequence);
        message.putLong(INTENDED_SEND_TIME_OFFSET, intendedSendTime);
        inbound.write(NEW_ORDER_MSG_TYPE_ID, message, 0, MESSAGE_LENGTH);
    }

    private static RingBuffer ringBuffer() {
        return new OneToOneRingBuffer(
                new UnsafeBuffer(ByteBuffer.allocateDirect(64 * 1024 + RingBufferDescriptor.TRAILER_LENGTH)));
    }
}

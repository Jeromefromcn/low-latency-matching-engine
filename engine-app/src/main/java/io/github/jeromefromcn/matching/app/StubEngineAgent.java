package io.github.jeromefromcn.matching.app;

import static io.github.jeromefromcn.matching.common.LatencyRecording.HIGHEST_TRACKABLE_LATENCY_NANOS;
import static io.github.jeromefromcn.matching.common.SkeletonProtocol.ACK_MSG_TYPE_ID;
import static io.github.jeromefromcn.matching.common.SkeletonProtocol.NEW_ORDER_MSG_TYPE_ID;

import org.HdrHistogram.SingleWriterRecorder;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.MessageHandler;
import org.agrona.concurrent.NanoClock;
import org.agrona.concurrent.ringbuffer.RingBuffer;

/**
 * Walking-skeleton engine (ROADMAP 0.4): acknowledges every new order, keeps no book. Replaced by the
 * Phase 2 engine in 3.1.
 *
 * <p>Engine latency (docs/benchmark-methodology.md §1) runs from the message being read to its
 * acknowledgement being written. Hot path: no allocation per message.
 */
final class StubEngineAgent implements Agent, MessageHandler {

    private final NanoClock clock;
    private final RingBuffer inbound;
    private final RingBuffer outbound;
    private final SingleWriterRecorder engineLatency;

    StubEngineAgent(NanoClock clock, RingBuffer inbound, RingBuffer outbound, SingleWriterRecorder engineLatency) {
        this.clock = clock;
        this.inbound = inbound;
        this.outbound = outbound;
        this.engineLatency = engineLatency;
    }

    @Override
    public int doWork() {
        return inbound.read(this);
    }

    @Override
    public void onMessage(int msgTypeId, MutableDirectBuffer buffer, int index, int length) {
        if (msgTypeId != NEW_ORDER_MSG_TYPE_ID) {
            return;
        }
        long readAt = clock.nanoTime();
        // A full outbound ring holds the engine here until the load generator drains it. The
        // pipeline in 3.1 replaces this with an explicit back-pressure policy.
        while (!outbound.write(ACK_MSG_TYPE_ID, buffer, index, length)) {
            Thread.onSpinWait();
        }
        engineLatency.recordValue(Math.min(clock.nanoTime() - readAt, HIGHEST_TRACKABLE_LATENCY_NANOS));
    }

    @Override
    public String roleName() {
        return "engine";
    }
}

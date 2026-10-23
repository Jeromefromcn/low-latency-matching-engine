package io.github.jeromefromcn.matching.loadgen;

import static io.github.jeromefromcn.matching.common.LatencyRecording.HIGHEST_TRACKABLE_LATENCY_NANOS;
import static io.github.jeromefromcn.matching.common.SkeletonProtocol.ACK_MSG_TYPE_ID;
import static io.github.jeromefromcn.matching.common.SkeletonProtocol.INTENDED_SEND_TIME_OFFSET;
import static io.github.jeromefromcn.matching.common.SkeletonProtocol.MESSAGE_LENGTH;
import static io.github.jeromefromcn.matching.common.SkeletonProtocol.NEW_ORDER_MSG_TYPE_ID;
import static io.github.jeromefromcn.matching.common.SkeletonProtocol.SEQUENCE_OFFSET;

import org.HdrHistogram.SingleWriterRecorder;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.MessageHandler;
import org.agrona.concurrent.NanoClock;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.RingBuffer;

/**
 * Sends every message that is due according to the {@link ConstantRateSchedule} and records the
 * pipeline latency of each acknowledgement, measured from the message's intended send time.
 *
 * <p>Hot path: no allocation per message. The agent is its own {@link MessageHandler} so reading
 * acknowledgements does not create a lambda.
 */
public final class LoadGeneratorAgent implements Agent, MessageHandler {

    private final NanoClock clock;
    private final ConstantRateSchedule schedule;
    private final RingBuffer inbound;
    private final RingBuffer outbound;
    private final SingleWriterRecorder pipelineLatency;
    private final UnsafeBuffer sendBuffer = new UnsafeBuffer(new byte[MESSAGE_LENGTH]);

    private long nextSequence;

    public LoadGeneratorAgent(
            NanoClock clock,
            ConstantRateSchedule schedule,
            RingBuffer inbound,
            RingBuffer outbound,
            SingleWriterRecorder pipelineLatency) {
        this.clock = clock;
        this.schedule = schedule;
        this.inbound = inbound;
        this.outbound = outbound;
        this.pipelineLatency = pipelineLatency;
    }

    @Override
    public int doWork() {
        return sendDueMessages() + outbound.read(this);
    }

    /** A message the ring refuses stays due and is retried on the next call, keeping its intended time. */
    private int sendDueMessages() {
        long now = clock.nanoTime();
        int sent = 0;
        long intendedSendTime;
        while ((intendedSendTime = schedule.intendedSendTimeNanos(nextSequence)) <= now) {
            sendBuffer.putLong(SEQUENCE_OFFSET, nextSequence);
            sendBuffer.putLong(INTENDED_SEND_TIME_OFFSET, intendedSendTime);
            if (!inbound.write(NEW_ORDER_MSG_TYPE_ID, sendBuffer, 0, MESSAGE_LENGTH)) {
                break;
            }
            nextSequence++;
            sent++;
        }
        return sent;
    }

    @Override
    public void onMessage(int msgTypeId, MutableDirectBuffer buffer, int index, int length) {
        if (msgTypeId == ACK_MSG_TYPE_ID) {
            long latency = clock.nanoTime() - buffer.getLong(index + INTENDED_SEND_TIME_OFFSET);
            pipelineLatency.recordValue(Math.min(latency, HIGHEST_TRACKABLE_LATENCY_NANOS));
        }
    }

    @Override
    public String roleName() {
        return "loadgen";
    }
}

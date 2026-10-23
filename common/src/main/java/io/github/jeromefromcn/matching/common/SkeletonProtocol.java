package io.github.jeromefromcn.matching.common;

/**
 * Placeholder wire layout for the walking skeleton (ROADMAP 0.4); replaced by SBE codecs in 2.1.
 *
 * <p>Both message types carry the same 16-byte payload: the load generator's sequence number and
 * the intended send time, echoed back unchanged in the acknowledgement.
 */
public final class SkeletonProtocol {

    public static final int NEW_ORDER_MSG_TYPE_ID = 1;
    public static final int ACK_MSG_TYPE_ID = 2;

    public static final int SEQUENCE_OFFSET = 0;
    public static final int INTENDED_SEND_TIME_OFFSET = 8;
    public static final int MESSAGE_LENGTH = 16;

    private SkeletonProtocol() {}
}

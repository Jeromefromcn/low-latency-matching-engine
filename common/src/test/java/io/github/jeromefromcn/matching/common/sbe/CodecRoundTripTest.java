package io.github.jeromefromcn.matching.common.sbe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Every message survives encode then decode with every field intact, for arbitrary field values
 * (jqwik includes the edge cases: 0, -1, MIN_VALUE, MAX_VALUE).
 *
 * <p>Decoding goes through the header the way a consumer does: read the header, check the
 * template, then wrap the decoder with the header's block length and version. Messages are written
 * at a non-zero offset into a buffer pre-filled with a guard byte, so a codec that ignores the
 * offset or writes past its encoded length fails the test.
 */
class CodecRoundTripTest {

    private static final int OFFSET = 16;
    private static final byte GUARD = (byte) 0x5A;

    private final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

    @Property
    void newOrderRoundTrips(
            @ForAll long clientOrderId,
            @ForAll long accountId,
            @ForAll long timestamp,
            @ForAll long price,
            @ForAll long quantity,
            @ForAll int instrumentId,
            @ForAll("sides") Side side,
            @ForAll("ordTypes") OrdType ordType,
            @ForAll("timesInForce") TimeInForce timeInForce) {
        fillWithGuard();
        NewOrderEncoder encoder = new NewOrderEncoder()
                .wrapAndApplyHeader(buffer, OFFSET, headerEncoder)
                .clientOrderId(clientOrderId)
                .accountId(accountId)
                .timestamp(timestamp)
                .price(price)
                .quantity(quantity)
                .instrumentId(instrumentId)
                .side(side)
                .ordType(ordType)
                .timeInForce(timeInForce);
        assertGuardIntact(encoder.encodedLength());

        readHeader(NewOrderDecoder.TEMPLATE_ID, NewOrderDecoder.BLOCK_LENGTH);
        NewOrderDecoder decoder =
                new NewOrderDecoder().wrap(buffer, bodyOffset(), headerDecoder.blockLength(), headerDecoder.version());

        assertEquals(clientOrderId, decoder.clientOrderId());
        assertEquals(accountId, decoder.accountId());
        assertEquals(timestamp, decoder.timestamp());
        assertEquals(price, decoder.price());
        assertEquals(quantity, decoder.quantity());
        assertEquals(instrumentId, decoder.instrumentId());
        assertEquals(side, decoder.side());
        assertEquals(ordType, decoder.ordType());
        assertEquals(timeInForce, decoder.timeInForce());
    }

    @Property
    void cancelOrderRoundTrips(
            @ForAll long clientOrderId,
            @ForAll long orderId,
            @ForAll long accountId,
            @ForAll long timestamp,
            @ForAll int instrumentId) {
        fillWithGuard();
        CancelOrderEncoder encoder = new CancelOrderEncoder()
                .wrapAndApplyHeader(buffer, OFFSET, headerEncoder)
                .clientOrderId(clientOrderId)
                .orderId(orderId)
                .accountId(accountId)
                .timestamp(timestamp)
                .instrumentId(instrumentId);
        assertGuardIntact(encoder.encodedLength());

        readHeader(CancelOrderDecoder.TEMPLATE_ID, CancelOrderDecoder.BLOCK_LENGTH);
        CancelOrderDecoder decoder = new CancelOrderDecoder()
                .wrap(buffer, bodyOffset(), headerDecoder.blockLength(), headerDecoder.version());

        assertEquals(clientOrderId, decoder.clientOrderId());
        assertEquals(orderId, decoder.orderId());
        assertEquals(accountId, decoder.accountId());
        assertEquals(timestamp, decoder.timestamp());
        assertEquals(instrumentId, decoder.instrumentId());
    }

    @Property
    void orderAcceptedRoundTrips(
            @ForAll long orderId,
            @ForAll long clientOrderId,
            @ForAll long accountId,
            @ForAll long timestamp,
            @ForAll long price,
            @ForAll long quantity,
            @ForAll int instrumentId,
            @ForAll("sides") Side side) {
        fillWithGuard();
        OrderAcceptedEncoder encoder = new OrderAcceptedEncoder()
                .wrapAndApplyHeader(buffer, OFFSET, headerEncoder)
                .orderId(orderId)
                .clientOrderId(clientOrderId)
                .accountId(accountId)
                .timestamp(timestamp)
                .price(price)
                .quantity(quantity)
                .instrumentId(instrumentId)
                .side(side);
        assertGuardIntact(encoder.encodedLength());

        readHeader(OrderAcceptedDecoder.TEMPLATE_ID, OrderAcceptedDecoder.BLOCK_LENGTH);
        OrderAcceptedDecoder decoder = new OrderAcceptedDecoder()
                .wrap(buffer, bodyOffset(), headerDecoder.blockLength(), headerDecoder.version());

        assertEquals(orderId, decoder.orderId());
        assertEquals(clientOrderId, decoder.clientOrderId());
        assertEquals(accountId, decoder.accountId());
        assertEquals(timestamp, decoder.timestamp());
        assertEquals(price, decoder.price());
        assertEquals(quantity, decoder.quantity());
        assertEquals(instrumentId, decoder.instrumentId());
        assertEquals(side, decoder.side());
    }

    @Property
    void orderRejectedRoundTrips(
            @ForAll long clientOrderId,
            @ForAll long accountId,
            @ForAll long timestamp,
            @ForAll int instrumentId,
            @ForAll("rejectReasons") RejectReason reason) {
        fillWithGuard();
        OrderRejectedEncoder encoder = new OrderRejectedEncoder()
                .wrapAndApplyHeader(buffer, OFFSET, headerEncoder)
                .clientOrderId(clientOrderId)
                .accountId(accountId)
                .timestamp(timestamp)
                .instrumentId(instrumentId)
                .reason(reason);
        assertGuardIntact(encoder.encodedLength());

        readHeader(OrderRejectedDecoder.TEMPLATE_ID, OrderRejectedDecoder.BLOCK_LENGTH);
        OrderRejectedDecoder decoder = new OrderRejectedDecoder()
                .wrap(buffer, bodyOffset(), headerDecoder.blockLength(), headerDecoder.version());

        assertEquals(clientOrderId, decoder.clientOrderId());
        assertEquals(accountId, decoder.accountId());
        assertEquals(timestamp, decoder.timestamp());
        assertEquals(instrumentId, decoder.instrumentId());
        assertEquals(reason, decoder.reason());
    }

    @Property
    void tradeRoundTrips(
            @ForAll long tradeId,
            @ForAll long buyOrderId,
            @ForAll long sellOrderId,
            @ForAll long timestamp,
            @ForAll long price,
            @ForAll long quantity,
            @ForAll int instrumentId,
            @ForAll("sides") Side aggressorSide) {
        fillWithGuard();
        TradeEncoder encoder = new TradeEncoder()
                .wrapAndApplyHeader(buffer, OFFSET, headerEncoder)
                .tradeId(tradeId)
                .buyOrderId(buyOrderId)
                .sellOrderId(sellOrderId)
                .timestamp(timestamp)
                .price(price)
                .quantity(quantity)
                .instrumentId(instrumentId)
                .aggressorSide(aggressorSide);
        assertGuardIntact(encoder.encodedLength());

        readHeader(TradeDecoder.TEMPLATE_ID, TradeDecoder.BLOCK_LENGTH);
        TradeDecoder decoder =
                new TradeDecoder().wrap(buffer, bodyOffset(), headerDecoder.blockLength(), headerDecoder.version());

        assertEquals(tradeId, decoder.tradeId());
        assertEquals(buyOrderId, decoder.buyOrderId());
        assertEquals(sellOrderId, decoder.sellOrderId());
        assertEquals(timestamp, decoder.timestamp());
        assertEquals(price, decoder.price());
        assertEquals(quantity, decoder.quantity());
        assertEquals(instrumentId, decoder.instrumentId());
        assertEquals(aggressorSide, decoder.aggressorSide());
    }

    @Property
    void orderCancelledRoundTrips(
            @ForAll long orderId,
            @ForAll long clientOrderId,
            @ForAll long timestamp,
            @ForAll long cancelledQuantity,
            @ForAll int instrumentId,
            @ForAll("cancelReasons") CancelReason reason) {
        fillWithGuard();
        OrderCancelledEncoder encoder = new OrderCancelledEncoder()
                .wrapAndApplyHeader(buffer, OFFSET, headerEncoder)
                .orderId(orderId)
                .clientOrderId(clientOrderId)
                .timestamp(timestamp)
                .cancelledQuantity(cancelledQuantity)
                .instrumentId(instrumentId)
                .reason(reason);
        assertGuardIntact(encoder.encodedLength());

        readHeader(OrderCancelledDecoder.TEMPLATE_ID, OrderCancelledDecoder.BLOCK_LENGTH);
        OrderCancelledDecoder decoder = new OrderCancelledDecoder()
                .wrap(buffer, bodyOffset(), headerDecoder.blockLength(), headerDecoder.version());

        assertEquals(orderId, decoder.orderId());
        assertEquals(clientOrderId, decoder.clientOrderId());
        assertEquals(timestamp, decoder.timestamp());
        assertEquals(cancelledQuantity, decoder.cancelledQuantity());
        assertEquals(instrumentId, decoder.instrumentId());
        assertEquals(reason, decoder.reason());
    }

    @Property
    void cancelRejectedRoundTrips(
            @ForAll long clientOrderId,
            @ForAll long orderId,
            @ForAll long accountId,
            @ForAll long timestamp,
            @ForAll int instrumentId,
            @ForAll("cancelRejectReasons") CancelRejectReason reason) {
        fillWithGuard();
        CancelRejectedEncoder encoder = new CancelRejectedEncoder()
                .wrapAndApplyHeader(buffer, OFFSET, headerEncoder)
                .clientOrderId(clientOrderId)
                .orderId(orderId)
                .accountId(accountId)
                .timestamp(timestamp)
                .instrumentId(instrumentId)
                .reason(reason);
        assertGuardIntact(encoder.encodedLength());

        readHeader(CancelRejectedDecoder.TEMPLATE_ID, CancelRejectedDecoder.BLOCK_LENGTH);
        CancelRejectedDecoder decoder = new CancelRejectedDecoder()
                .wrap(buffer, bodyOffset(), headerDecoder.blockLength(), headerDecoder.version());

        assertEquals(clientOrderId, decoder.clientOrderId());
        assertEquals(orderId, decoder.orderId());
        assertEquals(accountId, decoder.accountId());
        assertEquals(timestamp, decoder.timestamp());
        assertEquals(instrumentId, decoder.instrumentId());
        assertEquals(reason, decoder.reason());
    }

    @Provide
    Arbitrary<Side> sides() {
        return validValues(Side.class);
    }

    @Provide
    Arbitrary<OrdType> ordTypes() {
        return validValues(OrdType.class);
    }

    @Provide
    Arbitrary<TimeInForce> timesInForce() {
        return validValues(TimeInForce.class);
    }

    @Provide
    Arbitrary<RejectReason> rejectReasons() {
        return validValues(RejectReason.class);
    }

    @Provide
    Arbitrary<CancelReason> cancelReasons() {
        return validValues(CancelReason.class);
    }

    @Provide
    Arbitrary<CancelRejectReason> cancelRejectReasons() {
        return validValues(CancelRejectReason.class);
    }

    /** Every constant except SBE's generated NULL_VAL, which is "no value", not a valid one. */
    private static <E extends Enum<E>> Arbitrary<E> validValues(Class<E> type) {
        return Arbitraries.of(type).filter(value -> !value.name().equals("NULL_VAL"));
    }

    private void fillWithGuard() {
        buffer.setMemory(0, buffer.capacity(), GUARD);
    }

    /** Nothing before the message or after header + body may have been written. */
    private void assertGuardIntact(int bodyLength) {
        int end = OFFSET + MessageHeaderEncoder.ENCODED_LENGTH + bodyLength;
        for (int i = 0; i < OFFSET; i++) {
            assertEquals(GUARD, buffer.getByte(i), "byte before message at " + i);
        }
        for (int i = end; i < buffer.capacity(); i++) {
            assertEquals(GUARD, buffer.getByte(i), "byte after message at " + i);
        }
    }

    private void readHeader(int expectedTemplateId, int expectedBlockLength) {
        headerDecoder.wrap(buffer, OFFSET);
        assertEquals(expectedTemplateId, headerDecoder.templateId());
        assertEquals(expectedBlockLength, headerDecoder.blockLength());
        assertEquals(MessageHeaderDecoder.SCHEMA_ID, headerDecoder.schemaId());
        assertEquals(MessageHeaderDecoder.SCHEMA_VERSION, headerDecoder.version());
    }

    private static int bodyOffset() {
        return OFFSET + MessageHeaderDecoder.ENCODED_LENGTH;
    }
}

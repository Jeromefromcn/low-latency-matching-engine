package io.github.jeromefromcn.matching.common.sbe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/** Pins the schema conventions from ADR 0005 so that an edit breaking one fails the build. */
class WireLayoutTest {

    private static final List<Class<?>> INBOUND = List.of(NewOrderDecoder.class, CancelOrderDecoder.class);
    private static final List<Class<?>> OUTBOUND = List.of(
            OrderAcceptedDecoder.class,
            OrderRejectedDecoder.class,
            TradeDecoder.class,
            OrderCancelledDecoder.class,
            CancelRejectedDecoder.class);

    @Test
    void everyFieldIsNaturallyAlignedAndBlocksHaveNoGaps() throws ReflectiveOperationException {
        for (Class<?> decoder : allMessages()) {
            int end = 0;
            for (Field field : fieldsByOffset(decoder)) {
                String where = decoder.getSimpleName() + "." + field.name;
                assertEquals(0, field.offset % field.length, where + " is not aligned to its size");
                assertEquals(end, field.offset, where + " leaves a gap or overlaps the previous field");
                end = field.offset + field.length;
            }
            assertEquals(blockLength(decoder), end, decoder.getSimpleName() + " block length");
        }
    }

    @Test
    void blockLengthsAreUnchanged() throws ReflectiveOperationException {
        // Journals (ROADMAP 3.2) store these bytes. Changing a length means changing the wire format:
        // bump the schema version and update this table deliberately.
        assertEquals(47, blockLength(NewOrderDecoder.class));
        assertEquals(36, blockLength(CancelOrderDecoder.class));
        assertEquals(53, blockLength(OrderAcceptedDecoder.class));
        assertEquals(29, blockLength(OrderRejectedDecoder.class));
        assertEquals(53, blockLength(TradeDecoder.class));
        assertEquals(37, blockLength(OrderCancelledDecoder.class));
        assertEquals(37, blockLength(CancelRejectedDecoder.class));
        assertEquals(8, MessageHeaderDecoder.ENCODED_LENGTH);
    }

    @Test
    void templateIdsSeparateInboundRequestsFromOutboundEvents() throws ReflectiveOperationException {
        for (Class<?> decoder : INBOUND) {
            int id = templateId(decoder);
            assertTrue(id >= 1 && id <= 9, decoder.getSimpleName() + " template id " + id);
        }
        for (Class<?> decoder : OUTBOUND) {
            int id = templateId(decoder);
            assertTrue(id >= 10 && id <= 19, decoder.getSimpleName() + " template id " + id);
        }
    }

    @Test
    void zeroIsNotAValidValueOfAnyEnum() {
        // An uninitialised (all-zero) buffer must not decode as BUY, LIMIT, DAY or a real reason.
        assertThrows(IllegalArgumentException.class, () -> Side.get((short) 0));
        assertThrows(IllegalArgumentException.class, () -> OrdType.get((short) 0));
        assertThrows(IllegalArgumentException.class, () -> TimeInForce.get((short) 0));
        assertThrows(IllegalArgumentException.class, () -> RejectReason.get((short) 0));
        assertThrows(IllegalArgumentException.class, () -> CancelReason.get((short) 0));
        assertThrows(IllegalArgumentException.class, () -> CancelRejectReason.get((short) 0));
    }

    @Test
    void enumGetterThrowsOnAnUnknownByteSoTheHotPathMustReadTheRawValue() {
        // The generated side() allocates and throws on an unknown value. The engine reads sideRaw()
        // and turns a bad value into OrderRejected{INVALID_SIDE} instead (ADR 0005).
        UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
        new NewOrderEncoder().wrap(buffer, 0).side(Side.BUY);
        buffer.putByte(NewOrderDecoder.sideEncodingOffset(), (byte) 7);

        NewOrderDecoder decoder =
                new NewOrderDecoder().wrap(buffer, 0, NewOrderDecoder.BLOCK_LENGTH, NewOrderDecoder.SCHEMA_VERSION);

        assertEquals(7, decoder.sideRaw());
        assertThrows(IllegalArgumentException.class, decoder::side);
    }

    private record Field(String name, int offset, int length) {}

    private static List<Class<?>> allMessages() {
        List<Class<?>> all = new ArrayList<>(INBOUND);
        all.addAll(OUTBOUND);
        return all;
    }

    /** Fields as described by the generated static {@code <name>EncodingOffset/Length()} methods. */
    private static List<Field> fieldsByOffset(Class<?> decoder) throws ReflectiveOperationException {
        String suffix = "EncodingOffset";
        List<Field> fields = new ArrayList<>();
        for (Method method : decoder.getMethods()) {
            String name = method.getName();
            if (name.endsWith(suffix) && method.getParameterCount() == 0) {
                String field = name.substring(0, name.length() - suffix.length());
                int offset = (int) method.invoke(null);
                int length = (int) decoder.getMethod(field + "EncodingLength").invoke(null);
                fields.add(new Field(field, offset, length));
            }
        }
        assertTrue(!fields.isEmpty(), decoder.getSimpleName() + " has no fields");
        fields.sort(Comparator.comparingInt(Field::offset));
        return fields;
    }

    private static int blockLength(Class<?> decoder) throws ReflectiveOperationException {
        return decoder.getField("BLOCK_LENGTH").getInt(null);
    }

    private static int templateId(Class<?> decoder) throws ReflectiveOperationException {
        return decoder.getField("TEMPLATE_ID").getInt(null);
    }
}

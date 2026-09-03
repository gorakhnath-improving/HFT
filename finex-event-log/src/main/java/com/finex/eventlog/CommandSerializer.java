package com.finex.eventlog;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;

import com.finex.protocol.BinaryCodec;
import com.finex.protocol.ProtocolMessage;

/**
 * Converts {@link SubmitOrderCommand} / {@link CancelOrderCommand} to/from {@link Event}s using
 * the {@link BinaryCodec}.
 */
public final class CommandSerializer {

    private CommandSerializer() {
    }

    public static Event toEvent(SubmitOrderCommand command, Instant timestamp, long id) {
        ProtocolMessage.NewOrder message = new ProtocolMessage.NewOrder(
                command.accountId(),
                command.clientOrderId(),
                command.symbol(),
                command.side(),
                command.type(),
                command.price(),
                command.quantity());
        ByteBuffer buffer = BinaryCodec.encode(message);
        byte[] payload = new byte[buffer.remaining()];
        buffer.get(payload);
        return new Event(id, timestamp, Event.SUBMIT_ORDER, payload);
    }

    public static Event toEvent(CancelOrderCommand command, Instant timestamp, long id) {
        ProtocolMessage.CancelOrder message = new ProtocolMessage.CancelOrder(
                command.accountId(), command.orderId());
        ByteBuffer buffer = BinaryCodec.encode(message);
        byte[] payload = new byte[buffer.remaining()];
        buffer.get(payload);
        return new Event(id, timestamp, Event.CANCEL_ORDER, payload);
    }

    public static byte[] toPayload(SubmitOrderCommand command) {
        ProtocolMessage.NewOrder message = new ProtocolMessage.NewOrder(
                command.accountId(),
                command.clientOrderId(),
                command.symbol(),
                command.side(),
                command.type(),
                command.price(),
                command.quantity());
        return BinaryCodec.encodeToBytes(message);
    }

    public static byte[] toPayload(CancelOrderCommand command) {
        ProtocolMessage.CancelOrder message = new ProtocolMessage.CancelOrder(
                command.accountId(), command.orderId());
        return BinaryCodec.encodeToBytes(message);
    }

    public static SubmitOrderCommand toSubmitOrderCommand(Event event) {
        if (!Event.SUBMIT_ORDER.equals(event.type())) {
            throw new IllegalArgumentException("event type must be " + Event.SUBMIT_ORDER);
        }
        ProtocolMessage.NewOrder message = (ProtocolMessage.NewOrder) BinaryCodec.decode(
                ByteBuffer.wrap(event.payload()));
        return new SubmitOrderCommand(
                message.accountId(),
                message.clientOrderId(),
                message.symbol(),
                message.side(),
                message.type(),
                message.price(),
                message.quantity());
    }

    public static CancelOrderCommand toCancelOrderCommand(Event event) {
        if (!Event.CANCEL_ORDER.equals(event.type())) {
            throw new IllegalArgumentException("event type must be " + Event.CANCEL_ORDER);
        }
        ProtocolMessage.CancelOrder message = (ProtocolMessage.CancelOrder) BinaryCodec.decode(
                ByteBuffer.wrap(event.payload()));
        return new CancelOrderCommand(message.accountId(), message.orderId());
    }
}

package com.finex.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import com.finex.common.domain.enums.OrderStatus;
import com.finex.common.domain.enums.OrderType;
import com.finex.common.domain.enums.Side;

/**
 * Compact binary codec for the FinEx trading protocol.
 *
 * <p>Frame layout:
 * <pre>
 * | 4 bytes payload length | 1 byte message type | payload ... |
 * </pre>
 *
 * <p>Strings are encoded as a 4-byte length followed by UTF-8 bytes. A length of {@code -1}
 * represents {@code null}. {@link BigDecimal} values are encoded as their plain string form.
 * Enums are encoded as single byte ordinals; enum order is therefore part of the wire format.
 */
public final class BinaryCodec {

    public static final byte NEW_ORDER = 1;
    public static final byte CANCEL_ORDER = 2;
    public static final byte MODIFY_ORDER = 3;
    public static final byte ORDER_ACK = 4;
    public static final byte ORDER_REJECTED = 5;
    public static final byte EXECUTION_REPORT = 6;

    // Reusable per-thread encode buffer. It grows to the largest message seen by the
    // thread and is reset between calls. It must not be shared across threads or calls.
    private static final ThreadLocal<ByteArrayOutputStream> ENCODE_BAOS =
            ThreadLocal.withInitial(() -> new ByteArrayOutputStream(256));

    private BinaryCodec() {
    }

    /**
     * Encodes a message to a {@link ByteBuffer} with a 4-byte length prefix.
     */
    public static ByteBuffer encode(ProtocolMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        ByteArrayOutputStream baos = ENCODE_BAOS.get();
        baos.reset();
        DataOutputStream out = new DataOutputStream(baos);
        try {
            out.writeByte(typeOf(message));
            encodePayload(out, message);
            out.flush();

            byte[] payload = baos.toByteArray();
            ByteBuffer buffer = ByteBuffer.allocate(4 + payload.length);
            buffer.putInt(payload.length);
            buffer.put(payload);
            buffer.flip();
            return buffer;
        } catch (IOException e) {
            throw new ProtocolEncodeException("failed to encode message: " + message, e);
        }
    }

    /**
     * Encodes a message to a fresh {@code byte[]} with a 4-byte length prefix.
     * This avoids the intermediate {@link ByteBuffer} allocation and copy done by
     * {@link #encode(ProtocolMessage)} and is intended for the hot event-log path.
     */
    public static byte[] encodeToBytes(ProtocolMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        ByteArrayOutputStream baos = ENCODE_BAOS.get();
        baos.reset();
        DataOutputStream out = new DataOutputStream(baos);
        try {
            // Reserve 4 bytes for the length prefix; patched once the payload size is known.
            out.writeInt(0);
            out.writeByte(typeOf(message));
            encodePayload(out, message);
            out.flush();

            byte[] frame = baos.toByteArray();
            int payloadLength = frame.length - 4;
            frame[0] = (byte) (payloadLength >>> 24);
            frame[1] = (byte) (payloadLength >>> 16);
            frame[2] = (byte) (payloadLength >>> 8);
            frame[3] = (byte) payloadLength;
            return frame;
        } catch (IOException e) {
            throw new ProtocolEncodeException("failed to encode message: " + message, e);
        }
    }

    /**
     * Decodes a length-prefixed frame from the buffer. The buffer must contain exactly one
     * complete frame; partial reads are not supported by this baseline codec.
     */
    public static ProtocolMessage decode(ByteBuffer buffer) {
        if (buffer == null) {
            throw new IllegalArgumentException("buffer must not be null");
        }
        if (buffer.remaining() < 4) {
            throw new ProtocolDecodeException("buffer too short for length header");
        }
        int payloadLength = buffer.getInt();
        if (payloadLength < 1 || payloadLength > buffer.remaining()) {
            throw new ProtocolDecodeException("invalid payload length: " + payloadLength);
        }
        byte[] payload = new byte[payloadLength];
        buffer.get(payload);
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            byte type = in.readByte();
            return decodePayload(type, in);
        } catch (IOException e) {
            throw new ProtocolDecodeException("failed to decode message", e);
        }
    }

    private static byte typeOf(ProtocolMessage message) {
        return switch (message) {
            case ProtocolMessage.NewOrder _ -> NEW_ORDER;
            case ProtocolMessage.CancelOrder _ -> CANCEL_ORDER;
            case ProtocolMessage.ModifyOrder _ -> MODIFY_ORDER;
            case ProtocolMessage.OrderAck _ -> ORDER_ACK;
            case ProtocolMessage.OrderRejected _ -> ORDER_REJECTED;
            case ProtocolMessage.ExecutionReport _ -> EXECUTION_REPORT;
        };
    }

    private static void encodePayload(DataOutputStream out, ProtocolMessage message) throws IOException {
        switch (message) {
            case ProtocolMessage.NewOrder m -> {
                out.writeLong(m.accountId());
                writeString(out, m.clientOrderId());
                writeString(out, m.symbol());
                out.writeByte(m.side().ordinal());
                out.writeByte(m.type().ordinal());
                writeNullableString(out, m.price() == null ? null : m.price().toPlainString());
                writeString(out, m.quantity().toPlainString());
            }
            case ProtocolMessage.CancelOrder m -> {
                out.writeLong(m.accountId());
                out.writeLong(m.orderId());
            }
            case ProtocolMessage.ModifyOrder m -> {
                out.writeLong(m.accountId());
                out.writeLong(m.orderId());
                writeNullableString(out, m.newPrice() == null ? null : m.newPrice().toPlainString());
                writeString(out, m.newQuantity().toPlainString());
            }
            case ProtocolMessage.OrderAck m -> {
                out.writeLong(m.orderId());
                writeString(out, m.clientOrderId());
                writeString(out, m.symbol());
                out.writeByte(m.status().ordinal());
                out.writeLong(m.accountId());
                out.writeLong(m.sequence());
            }
            case ProtocolMessage.OrderRejected m -> {
                out.writeLong(m.orderId());
                writeString(out, m.reason());
            }
            case ProtocolMessage.ExecutionReport m -> {
                out.writeLong(m.orderId());
                out.writeLong(m.accountId());
                writeString(out, m.symbol());
                out.writeByte(m.side().ordinal());
                writeString(out, m.price().toPlainString());
                writeString(out, m.quantity().toPlainString());
                writeString(out, m.remainingQuantity().toPlainString());
                out.writeByte(m.status().ordinal());
                out.writeLong(m.tradeId());
            }
        }
    }

    private static ProtocolMessage decodePayload(byte type, DataInputStream in) throws IOException {
        return switch (type) {
            case NEW_ORDER -> new ProtocolMessage.NewOrder(
                    in.readLong(),
                    readString(in),
                    readString(in),
                    Side.values()[in.readByte()],
                    OrderType.values()[in.readByte()],
                    readNullableBigDecimal(in),
                    new BigDecimal(readString(in)));
            case CANCEL_ORDER -> new ProtocolMessage.CancelOrder(in.readLong(), in.readLong());
            case MODIFY_ORDER -> new ProtocolMessage.ModifyOrder(
                    in.readLong(),
                    in.readLong(),
                    readNullableBigDecimal(in),
                    new BigDecimal(readString(in)));
            case ORDER_ACK -> new ProtocolMessage.OrderAck(
                    in.readLong(),
                    readString(in),
                    readString(in),
                    OrderStatus.values()[in.readByte()],
                    in.readLong(),
                    in.readLong());
            case ORDER_REJECTED -> new ProtocolMessage.OrderRejected(in.readLong(), readString(in));
            case EXECUTION_REPORT -> new ProtocolMessage.ExecutionReport(
                    in.readLong(),
                    in.readLong(),
                    readString(in),
                    Side.values()[in.readByte()],
                    new BigDecimal(readString(in)),
                    new BigDecimal(readString(in)),
                    new BigDecimal(readString(in)),
                    OrderStatus.values()[in.readByte()],
                    in.readLong());
            default -> throw new ProtocolDecodeException("unknown message type: " + type);
        };
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static void writeNullableString(DataOutputStream out, String value) throws IOException {
        if (value == null) {
            out.writeInt(-1);
        } else {
            writeString(out, value);
        }
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0) {
            throw new ProtocolDecodeException("unexpected null string");
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static BigDecimal readNullableBigDecimal(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length == -1) {
            return null;
        }
        if (length < 0) {
            throw new ProtocolDecodeException("invalid string length: " + length);
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new BigDecimal(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
    }
}

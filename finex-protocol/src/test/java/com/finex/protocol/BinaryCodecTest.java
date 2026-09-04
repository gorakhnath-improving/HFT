package com.finex.protocol;

import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;

import com.finex.common.domain.enums.OrderStatus;
import com.finex.common.domain.enums.OrderType;
import com.finex.common.domain.enums.Side;

import static org.assertj.core.api.Assertions.assertThat;

class BinaryCodecTest {

    @Test
    void roundTripNewOrder() {
        ProtocolMessage.NewOrder original = new ProtocolMessage.NewOrder(
                100L, "cid-1", "BTC-USD", Side.BUY, OrderType.LIMIT,
                new BigDecimal("50000"), new BigDecimal("2"));

        ByteBuffer encoded = BinaryCodec.encode(original);
        ProtocolMessage decoded = BinaryCodec.decode(encoded);

        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void roundTripNewOrderWithNullPrice() {
        ProtocolMessage.NewOrder original = new ProtocolMessage.NewOrder(
                200L, "cid-mkt", "BTC-USD", Side.SELL, OrderType.MARKET,
                null, new BigDecimal("1"));

        ByteBuffer encoded = BinaryCodec.encode(original);
        ProtocolMessage decoded = BinaryCodec.decode(encoded);

        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void roundTripCancelOrder() {
        ProtocolMessage.CancelOrder original = new ProtocolMessage.CancelOrder(100L, 42L);

        ByteBuffer encoded = BinaryCodec.encode(original);
        ProtocolMessage decoded = BinaryCodec.decode(encoded);

        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void roundTripModifyOrder() {
        ProtocolMessage.ModifyOrder original = new ProtocolMessage.ModifyOrder(
                100L, 42L, new BigDecimal("51000"), new BigDecimal("3"));

        ByteBuffer encoded = BinaryCodec.encode(original);
        ProtocolMessage decoded = BinaryCodec.decode(encoded);

        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void roundTripOrderAck() {
        ProtocolMessage.OrderAck original = new ProtocolMessage.OrderAck(
                1L, "cid-1", "BTC-USD", OrderStatus.OPEN, 100L, 5L);

        ByteBuffer encoded = BinaryCodec.encode(original);
        ProtocolMessage decoded = BinaryCodec.decode(encoded);

        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void roundTripOrderRejected() {
        ProtocolMessage.OrderRejected original = new ProtocolMessage.OrderRejected(
                1L, "price outside allowed collar");

        ByteBuffer encoded = BinaryCodec.encode(original);
        ProtocolMessage decoded = BinaryCodec.decode(encoded);

        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void roundTripExecutionReport() {
        ProtocolMessage.ExecutionReport original = new ProtocolMessage.ExecutionReport(
                1L, 100L, "BTC-USD", Side.BUY,
                new BigDecimal("50000"), new BigDecimal("1"), new BigDecimal("0"),
                OrderStatus.FILLED, 7L);

        ByteBuffer encoded = BinaryCodec.encode(original);
        ProtocolMessage decoded = BinaryCodec.decode(encoded);

        assertThat(decoded).isEqualTo(original);
    }
}

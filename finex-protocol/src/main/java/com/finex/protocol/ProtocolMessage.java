package com.finex.protocol;

import java.math.BigDecimal;

import com.finex.common.domain.enums.OrderStatus;
import com.finex.common.domain.enums.OrderType;
import com.finex.common.domain.enums.Side;

/**
 * Sealed binary protocol message types. Each message is an immutable record suitable for
 * direct encoding by {@link BinaryCodec}.
 */
public sealed interface ProtocolMessage permits
        ProtocolMessage.NewOrder,
        ProtocolMessage.CancelOrder,
        ProtocolMessage.ModifyOrder,
        ProtocolMessage.OrderAck,
        ProtocolMessage.OrderRejected,
        ProtocolMessage.ExecutionReport {

    /**
     * New inbound order request.
     */
    record NewOrder(
            long accountId,
            String clientOrderId,
            String symbol,
            Side side,
            OrderType type,
            BigDecimal price,
            BigDecimal quantity) implements ProtocolMessage {
    }

    /**
     * Cancel existing order request.
     */
    record CancelOrder(long accountId, long orderId) implements ProtocolMessage {
    }

    /**
     * Modify existing order request.
     */
    record ModifyOrder(
            long accountId,
            long orderId,
            BigDecimal newPrice,
            BigDecimal newQuantity) implements ProtocolMessage {
    }

    /**
     * Positive acknowledgement that an order was accepted by the gateway/risk engine.
     */
    record OrderAck(
            long orderId,
            String clientOrderId,
            String symbol,
            OrderStatus status,
            long accountId,
            long sequence) implements ProtocolMessage {
    }

    /**
     * Negative acknowledgement / rejection.
     */
    record OrderRejected(long orderId, String reason) implements ProtocolMessage {
    }

    /**
     * Execution report for a fill.
     */
    record ExecutionReport(
            long orderId,
            long accountId,
            String symbol,
            Side side,
            BigDecimal price,
            BigDecimal quantity,
            BigDecimal remainingQuantity,
            OrderStatus status,
            long tradeId) implements ProtocolMessage {
    }
}

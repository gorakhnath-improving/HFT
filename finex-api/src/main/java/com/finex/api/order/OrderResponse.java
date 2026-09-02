package com.finex.api.order;

import java.math.BigDecimal;
import java.util.List;

import com.finex.common.domain.Order;
import com.finex.common.domain.Trade;
import com.finex.common.domain.enums.OrderStatus;

/**
 * Response returned when querying or submitting an order.
 */
public record OrderResponse(
        long orderId,
        String clientOrderId,
        String symbol,
        com.finex.common.domain.enums.Side side,
        com.finex.common.domain.enums.OrderType type,
        BigDecimal price,
        BigDecimal quantity,
        BigDecimal remainingQuantity,
        OrderStatus status,
        boolean addedToBook,
        List<TradeView> trades) {

    public static OrderResponse from(long orderId, Order order, List<Trade> trades, boolean addedToBook) {
        return new OrderResponse(
                orderId,
                order.clientOrderId(),
                order.symbol(),
                order.side(),
                order.type(),
                order.price(),
                order.quantity(),
                order.remainingQuantity(),
                order.status(),
                addedToBook,
                trades.stream().map(TradeView::from).toList());
    }
}

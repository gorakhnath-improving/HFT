package com.finex.api.order;

import java.util.List;

import com.finex.common.domain.Order;

/**
 * Snapshot of the order book for a symbol.
 */
public record OrderBookView(String symbol, List<OrderResponse> bids, List<OrderResponse> asks) {

    public static OrderBookView from(String symbol, List<Order> bids, List<Order> asks) {
        return new OrderBookView(
                symbol,
                bids.stream().map(o -> OrderResponse.from(o.orderId(), o, List.of(), true)).toList(),
                asks.stream().map(o -> OrderResponse.from(o.orderId(), o, List.of(), true)).toList());
    }
}

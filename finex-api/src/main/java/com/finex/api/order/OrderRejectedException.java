package com.finex.api.order;

import com.finex.common.domain.Order;

/**
 * Thrown when the risk engine rejects an order. Carries the rejected order and the reason
 * so that the API can return a structured {@link OrderResponse} with status
 * {@code REJECTED}.
 */
public class OrderRejectedException extends RuntimeException {

    private final Order order;

    public OrderRejectedException(Order order, String reason) {
        super(reason);
        this.order = order;
    }

    public Order order() {
        return order;
    }

    public String reason() {
        return getMessage();
    }
}

package com.finex.api.order;

import java.math.BigDecimal;

import com.finex.common.domain.enums.OrderType;
import com.finex.common.domain.enums.Side;

/**
 * Request to submit a new order via the REST API.
 */
public record OrderRequest(
        String clientOrderId,
        String symbol,
        Side side,
        OrderType type,
        BigDecimal price,
        BigDecimal quantity,
        long accountId) {
}

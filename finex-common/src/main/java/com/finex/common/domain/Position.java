package com.finex.common.domain;

import java.math.BigDecimal;

/**
 * A holding position in a single asset for a single account.
 * P&L fields are tracked explicitly and updated by the portfolio engine (Phase 12).
 *
 * @param accountId        owning account
 * @param symbol           asset or instrument symbol
 * @param quantity         current quantity held; must be non-negative
 * @param averagePrice     average entry price of the holding
 * @param realizedPnl      realized profit/loss from closed trades
 * @param unrealizedPnl    unrealized P&L at current mark price
 */
public record Position(
        long accountId,
        String symbol,
        BigDecimal quantity,
        BigDecimal averagePrice,
        BigDecimal realizedPnl,
        BigDecimal unrealizedPnl) {

    public Position {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("quantity must not be negative");
        }
        if (averagePrice == null || averagePrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("averagePrice must not be negative");
        }
        if (realizedPnl == null) {
            throw new IllegalArgumentException("realizedPnl must not be null");
        }
        if (unrealizedPnl == null) {
            throw new IllegalArgumentException("unrealizedPnl must not be null");
        }
    }

    /**
     * Returns the total market value of the position at the given mark price.
     */
    public BigDecimal marketValue(BigDecimal markPrice) {
        return quantity.multiply(markPrice);
    }

    /**
     * Returns total P&L (realized + unrealized).
     */
    public BigDecimal totalPnl() {
        return realizedPnl.add(unrealizedPnl);
    }
}

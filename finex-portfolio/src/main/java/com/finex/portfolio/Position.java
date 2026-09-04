package com.finex.portfolio;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.finex.common.domain.enums.Side;

/**
 * Immutable position for a single symbol in an account. Quantity is signed: positive for
 * long, negative for short. Realized P&L is accumulated on closing portions; unrealized
 * P&L is computed from the last mark price.
 */
public record Position(
        String symbol,
        BigDecimal quantity,
        BigDecimal avgPrice,
        BigDecimal realizedPnl,
        BigDecimal unrealizedPnl) {

    private static final int SCALE = 20;

    public Position {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        if (quantity == null) {
            throw new IllegalArgumentException("quantity must not be null");
        }
        if (avgPrice == null || avgPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("avgPrice must not be negative");
        }
        if (realizedPnl == null) {
            throw new IllegalArgumentException("realizedPnl must not be null");
        }
        if (unrealizedPnl == null) {
            throw new IllegalArgumentException("unrealizedPnl must not be null");
        }
    }

    /**
     * Returns a new {@link Position} after applying a trade, using {@code tradePrice} as the
     * mark price for the resulting position.
     */
    public Position withTrade(Side side, BigDecimal tradePrice, BigDecimal tradeQty, BigDecimal markPrice) {
        if (tradePrice == null || tradePrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("tradePrice must be positive");
        }
        if (tradeQty == null || tradeQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("tradeQty must be positive");
        }

        BigDecimal signedTradeQty = side == Side.BUY ? tradeQty : tradeQty.negate();
        BigDecimal newQty = quantity.add(signedTradeQty);
        BigDecimal newRealized = realizedPnl;
        BigDecimal newCostBasis;
        BigDecimal newAvgPrice;

        if (quantity.signum() == 0) {
            newCostBasis = signedTradeQty.multiply(tradePrice);
            newAvgPrice = tradePrice;
        } else if (newQty.signum() == 0) {
            // Position fully closed.
            newRealized = realizedPnl.add(quantity.multiply(tradePrice.subtract(avgPrice)));
            newCostBasis = BigDecimal.ZERO;
            newAvgPrice = BigDecimal.ZERO;
        } else if (quantity.signum() * newQty.signum() > 0) {
            // Same side / reducing without crossing zero.
            newCostBasis = quantity.multiply(avgPrice).add(signedTradeQty.multiply(tradePrice));
            newAvgPrice = newCostBasis.divide(newQty, SCALE, RoundingMode.HALF_UP);
        } else {
            // Trade crosses from long to short or short to long.
            newRealized = realizedPnl.add(quantity.multiply(tradePrice.subtract(avgPrice)));
            newCostBasis = newQty.multiply(tradePrice);
            newAvgPrice = tradePrice;
        }

        // avgPrice can become slightly negative due to rounding when crossing; force positive.
        if (newAvgPrice.signum() < 0) {
            newAvgPrice = newAvgPrice.negate();
        }

        BigDecimal newUnrealized = newQty.multiply(markPrice.subtract(newAvgPrice));
        return new Position(symbol, newQty, newAvgPrice, newRealized, newUnrealized);
    }

    /**
     * Revalues the position at {@code markPrice} and returns a new {@link Position}.
     */
    public Position mark(BigDecimal markPrice) {
        if (markPrice == null || markPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("markPrice must be positive");
        }
        if (quantity.compareTo(BigDecimal.ZERO) == 0) {
            return this;
        }
        BigDecimal newUnrealized = quantity.multiply(markPrice.subtract(avgPrice));
        return new Position(symbol, quantity, avgPrice, realizedPnl, newUnrealized);
    }

    public BigDecimal exposure() {
        return quantity.abs().multiply(avgPrice);
    }
}

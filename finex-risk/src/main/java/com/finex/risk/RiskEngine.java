package com.finex.risk;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

import com.finex.common.domain.Order;
import com.finex.common.domain.Trade;
import com.finex.common.domain.enums.OrderType;
import com.finex.common.domain.enums.Side;

/**
 * Stateless pre-trade risk engine (Master Plan §10). Performs size, notional, price-collar,
 * position, cash-exposure, and rate-limit checks. The engine does not hold account state;
 * callers pass an {@link AccountRiskState} and update it via {@link #onTrade} and
 * {@link #onCancel} as the order lifecycle progresses.
 */
public class RiskEngine {

    private static final Duration RATE_LIMIT_WINDOW = Duration.ofSeconds(1);
    private static final int NOTIONAL_SCALE = 20;

    private final RiskConfig config;

    public RiskEngine(RiskConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        this.config = config;
    }

    public RiskConfig config() {
        return config;
    }

    /**
     * Validates an order against the account's risk state and, if accepted, reserves the
     * appropriate cash (BUY only) and projected position.
     *
     * @param order          the order to validate
     * @param state          the account's mutable risk state
     * @param now            submission timestamp (used for rate limit)
     * @param lastTradePrice last traded price for the symbol, or null if none
     * @return a {@link RiskResult} indicating acceptance or the reason for rejection
     */
    public RiskResult validate(Order order, AccountRiskState state, Instant now, BigDecimal lastTradePrice) {
        if (order == null) {
            throw new IllegalArgumentException("order must not be null");
        }
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }

        state.pruneTimestamps(now, RATE_LIMIT_WINDOW);
        if (state.ordersInWindow(now, RATE_LIMIT_WINDOW) >= config.maxOrdersPerSecond()) {
            return RiskResult.reject("rate limit exceeded");
        }

        if (order.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            return RiskResult.reject("quantity must be positive");
        }
        if (order.quantity().compareTo(config.maxOrderQuantity()) > 0) {
            return RiskResult.reject("quantity exceeds max order quantity");
        }

        BigDecimal price = order.price();
        if (order.type() == OrderType.LIMIT) {
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                return RiskResult.reject("LIMIT order must have a positive price");
            }
        } else if (order.type() == OrderType.MARKET) {
            if (lastTradePrice == null) {
                return RiskResult.reject("MARKET order rejected: no last trade price");
            }
            price = lastTradePrice;
        }

        BigDecimal notional = price.multiply(order.quantity());
        if (notional.compareTo(config.maxOrderNotional()) > 0) {
            return RiskResult.reject("order notional exceeds max order notional");
        }

        if (lastTradePrice != null && order.type() == OrderType.LIMIT) {
            BigDecimal deviation = order.price().subtract(lastTradePrice).abs()
                    .divide(lastTradePrice, NOTIONAL_SCALE, RoundingMode.HALF_UP);
            if (deviation.compareTo(config.priceCollarFraction()) > 0) {
                return RiskResult.reject("price outside allowed collar");
            }
        }

        BigDecimal signedQty = order.side() == Side.BUY ? order.quantity() : order.quantity().negate();
        BigDecimal projectedPosition = state.projectedPosition().add(signedQty);
        if (projectedPosition.abs().compareTo(config.maxPosition()) > 0) {
            return RiskResult.reject("order would exceed max position");
        }

        if (order.side() == Side.BUY) {
            BigDecimal totalOpenNotional = state.reservedCash().add(notional);
            if (totalOpenNotional.compareTo(config.maxCashExposure()) > 0) {
                return RiskResult.reject("total open notional would exceed max cash exposure");
            }
            if (notional.compareTo(state.availableCash()) > 0) {
                return RiskResult.reject("insufficient available cash");
            }
        }

        state.reserveOrder(order.orderId(), price, order.quantity(), order.side());
        state.addOrderTimestamp(now);
        return RiskResult.ok();
    }

    /**
     * Updates the account state after a trade has occurred.
     */
    public void onTrade(AccountRiskState state, long orderId, Trade trade, Side side) {
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        if (trade == null) {
            throw new IllegalArgumentException("trade must not be null");
        }
        state.applyTrade(orderId, trade, side);
    }

    /**
     * Releases the remaining reservation for an order when it is cancelled.
     */
    public void onCancel(AccountRiskState state, long orderId) {
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        state.releaseOrder(orderId);
    }
}

package com.finex.risk;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import com.finex.common.domain.Order;
import com.finex.common.domain.Trade;
import com.finex.common.domain.enums.OrderType;
import com.finex.common.domain.enums.Side;
import com.finex.common.numeric.FixedPoint;

public final class FixedPointRiskEngine {

    private static final Duration RATE_LIMIT_WINDOW = Duration.ofSeconds(1);

    private final RiskConfig config;
    private final long maxOrderQuantity;
    private final long maxOrderNotional;
    private final long maxPosition;
    private final long maxCashExposure;
    private final long priceCollarFraction;

    public FixedPointRiskEngine(RiskConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        this.config = config;
        this.maxOrderQuantity = FixedPoint.toRawExact(config.maxOrderQuantity());
        this.maxOrderNotional = FixedPoint.toRawExact(config.maxOrderNotional());
        this.maxPosition = FixedPoint.toRawExact(config.maxPosition());
        this.maxCashExposure = FixedPoint.toRawExact(config.maxCashExposure());
        this.priceCollarFraction = FixedPoint.toRawExact(config.priceCollarFraction());
    }

    public RiskConfig config() {
        return config;
    }

    public RiskResult validate(Order order, FixedPointAccountRiskState state, Instant now,
                               BigDecimal lastTradePrice) {
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

        long quantity = FixedPoint.toRawExact(order.quantity());
        if (quantity <= 0) {
            return RiskResult.reject("quantity must be positive");
        }
        if (quantity > maxOrderQuantity) {
            return RiskResult.reject("quantity exceeds max order quantity");
        }

        BigDecimal effectivePrice = order.price();
        if (order.type() == OrderType.LIMIT) {
            if (effectivePrice == null || effectivePrice.signum() <= 0) {
                return RiskResult.reject("LIMIT order must have a positive price");
            }
        } else if (lastTradePrice == null) {
            return RiskResult.reject("MARKET order rejected: no last trade price");
        } else {
            effectivePrice = lastTradePrice;
        }

        long price = FixedPoint.toRawExact(effectivePrice);
        long notional = FixedPoint.multiplyExactRaw(price, quantity);
        if (notional > maxOrderNotional) {
            return RiskResult.reject("order notional exceeds max order notional");
        }

        if (lastTradePrice != null && order.type() == OrderType.LIMIT) {
            long lastPrice = FixedPoint.toRawExact(lastTradePrice);
            long difference = absExact(Math.subtractExact(price, lastPrice));
            long allowedDifference = FixedPoint.multiplyExactRaw(lastPrice, priceCollarFraction);
            if (difference > allowedDifference) {
                return RiskResult.reject("price outside allowed collar");
            }
        }

        long signedQuantity = order.side() == Side.BUY ? quantity : Math.negateExact(quantity);
        long projectedPosition = Math.addExact(state.projectedPositionRaw(), signedQuantity);
        if (absExact(projectedPosition) > maxPosition) {
            return RiskResult.reject("order would exceed max position");
        }

        if (order.side() == Side.BUY) {
            long totalOpenNotional = Math.addExact(state.reservedCashRaw(), notional);
            if (totalOpenNotional > maxCashExposure) {
                return RiskResult.reject("total open notional would exceed max cash exposure");
            }
            if (notional > state.availableCashRaw()) {
                return RiskResult.reject("insufficient available cash");
            }
        }

        state.reserveOrder(order.orderId(), price, quantity, order.side());
        state.addOrderTimestamp(now);
        return RiskResult.ok();
    }

    public void onTrade(FixedPointAccountRiskState state, long orderId, Trade trade, Side side) {
        state.applyTrade(orderId, trade, side);
    }

    public void onCancel(FixedPointAccountRiskState state, long orderId) {
        state.releaseOrder(orderId);
    }

    private static long absExact(long value) {
        return value < 0 ? Math.negateExact(value) : value;
    }
}

package com.finex.api.order;

import java.math.BigDecimal;
import java.time.Instant;

import com.finex.common.domain.Trade;

/**
 * Serializable view of a matched trade.
 */
public record TradeView(
        long tradeId,
        String symbol,
        BigDecimal price,
        BigDecimal quantity,
        long buyOrderId,
        long sellOrderId,
        long buyerAccountId,
        long sellerAccountId,
        long tradeSequence,
        Instant timestamp) {

    public static TradeView from(Trade trade) {
        return new TradeView(
                trade.tradeId(),
                trade.symbol(),
                trade.price(),
                trade.quantity(),
                trade.buyOrderId(),
                trade.sellOrderId(),
                trade.buyerAccountId(),
                trade.sellerAccountId(),
                trade.tradeSequence(),
                trade.timestamp());
    }
}

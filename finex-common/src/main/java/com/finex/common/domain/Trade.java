package com.finex.common.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A matched trade between two orders.
 *
 * @param tradeId          unique trade identifier
 * @param buyOrderId       id of the order on the buy side
 * @param sellOrderId      id of the order on the sell side
 * @param symbol           instrument symbol
 * @param price            execution price; must be positive
 * @param quantity         executed quantity; must be positive
 * @param timestamp        execution time
 * @param buyerAccountId   account credited with the base asset
 * @param sellerAccountId  account debited of the base asset
 * @param tradeSequence    sequence number for deterministic replay (Master Plan §22)
 */
public record Trade(
        long tradeId,
        long buyOrderId,
        long sellOrderId,
        String symbol,
        BigDecimal price,
        BigDecimal quantity,
        Instant timestamp,
        long buyerAccountId,
        long sellerAccountId,
        long tradeSequence) {

    public Trade {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("price must be positive");
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp must not be null");
        }
    }
}

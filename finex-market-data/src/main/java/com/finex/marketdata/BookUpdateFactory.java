package com.finex.marketdata;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.finex.common.domain.Order;
import com.finex.orderbook.OrderBook;

/**
 * Builds {@link BookUpdate} snapshots from an {@link OrderBook}.
 */
public final class BookUpdateFactory {

    private BookUpdateFactory() {
    }

    public static BookUpdate from(String symbol, OrderBook book, Instant timestamp) {
        return new BookUpdate(
                symbol,
                aggregate(book.getBids()),
                aggregate(book.getAsks()),
                timestamp);
    }

    private static List<PriceLevel> aggregate(List<Order> orders) {
        List<PriceLevel> levels = new ArrayList<>();
        BigDecimal currentPrice = null;
        BigDecimal currentQty = BigDecimal.ZERO;
        for (Order order : orders) {
            BigDecimal price = order.price();
            BigDecimal qty = order.remainingQuantity();
            if (currentPrice != null && currentPrice.compareTo(price) != 0) {
                levels.add(new PriceLevel(currentPrice, currentQty));
                currentQty = qty;
            } else {
                currentQty = currentQty.add(qty);
            }
            currentPrice = price;
        }
        if (currentPrice != null) {
            levels.add(new PriceLevel(currentPrice, currentQty));
        }
        return levels;
    }
}

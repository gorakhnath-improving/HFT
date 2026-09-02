package com.finex.matching;

import java.util.List;

import com.finex.common.domain.Order;
import com.finex.common.domain.Trade;

/**
 * Result of matching an order against the order book.
 *
 * @param order        final state of the incoming order after matching
 * @param trades       trades generated during matching, in sequence order
 * @param addedToBook  true if the remaining quantity was placed in the order book
 */
public record MatchResult(Order order, List<Trade> trades, boolean addedToBook) {
}

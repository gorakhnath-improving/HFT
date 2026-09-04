package com.finex.portfolio;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.finex.common.domain.Trade;
import com.finex.common.domain.enums.Side;

/**
 * Maintains per-account positions and cash, and computes realized/unrealized P&L. The
 * service is intentionally in-memory and single-node for the baseline.
 */
public class PortfolioService {

    private static final BigDecimal DEFAULT_INITIAL_CASH = new BigDecimal("1000000");

    private final Map<Long, BigDecimal> cash = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, Position>> positions = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> lastMarkPrices = new ConcurrentHashMap<>();

    /**
     * Applies the position changes of a trade to both the buyer and seller. Cash is updated
     * separately via {@link #applyCashDelta} using the net amount from clearing.
     */
    public void applyTrade(Trade trade, BigDecimal markPrice) {
        if (trade == null) {
            throw new IllegalArgumentException("trade must not be null");
        }
        if (markPrice == null || markPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("markPrice must be positive");
        }

        updatePosition(trade.buyerAccountId(), trade.symbol(), Side.BUY, trade.price(), trade.quantity(), markPrice);
        updatePosition(trade.sellerAccountId(), trade.symbol(), Side.SELL, trade.price(), trade.quantity(), markPrice);
    }

    /**
     * Applies a net cash delta to an account. Clearing determines the signed amount.
     */
    public void applyCashDelta(long accountId, BigDecimal cashDelta) {
        cash.compute(accountId, (k, v) -> (v == null ? DEFAULT_INITIAL_CASH : v).add(cashDelta));
    }

    /**
     * Revalues all positions for {@code symbol} across all accounts at {@code markPrice}.
     *
     * <p>This is a no-op when the {@code markPrice} is the same as the last revaluation for
     * that symbol, because positions for the two accounts involved in a trade have already
     * been updated by {@link #applyTrade} at that mark price, and all other positions are
     * unchanged unless the mark price itself has moved.
     */
    public void markToMarket(String symbol, BigDecimal markPrice) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        if (markPrice == null || markPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("markPrice must be positive");
        }
        BigDecimal previous = lastMarkPrices.get(symbol);
        if (previous != null && previous.compareTo(markPrice) == 0) {
            return;
        }
        lastMarkPrices.put(symbol, markPrice);
        for (Map<String, Position> accountPositions : positions.values()) {
            Position position = accountPositions.get(symbol);
            if (position != null) {
                accountPositions.put(symbol, position.mark(markPrice));
            }
        }
    }

    public Portfolio portfolio(long accountId) {
        BigDecimal accountCash = cash.getOrDefault(accountId, DEFAULT_INITIAL_CASH);
        Map<String, Position> accountPositions = positions.getOrDefault(accountId, Map.of());
        List<Position> positionList = List.copyOf(accountPositions.values());
        BigDecimal unrealized = positionList.stream()
                .map(Position::unrealizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Portfolio(accountId, accountCash, positionList, accountCash.add(unrealized));
    }

    private void updatePosition(long accountId, String symbol, Side side, BigDecimal price, BigDecimal quantity,
                                BigDecimal markPrice) {
        Map<String, Position> accountPositions = positions.computeIfAbsent(accountId, k -> new ConcurrentHashMap<>());
        Position current = accountPositions.getOrDefault(symbol,
                new Position(symbol, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        Position updated = current.withTrade(side, price, quantity, markPrice);
        accountPositions.put(symbol, updated);
    }
}

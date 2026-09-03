package com.finex.benchmarks.stress;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.finex.api.order.OrderBookView;
import com.finex.api.order.OrderResponse;
import com.finex.api.order.OrderService;
import com.finex.ledger.LedgerEntry;
import com.finex.portfolio.Portfolio;
import com.finex.portfolio.Position;

/**
 * A canonical, comparison-friendly snapshot of everything a stress scenario touched in an
 * {@link OrderService}: orders (sorted by exchange-assigned order id), ledger entries
 * (already insertion/id ordered), portfolios (sorted by account id, positions sorted by
 * symbol), and top-of-book order-book state for every symbol used (already price/time
 * ordered by {@link com.finex.orderbook.OrderBook}, so no re-sorting is needed there).
 *
 * <p>Object identity, memory addresses, and hashCodes are never compared; only these
 * canonical field tuples are, via each record's generated {@code equals}.
 */
public record EngineSnapshot(
        Map<Long, OrderView> ordersByOrderId,
        List<LedgerEntryView> ledgerEntries,
        Map<Long, PortfolioView> portfoliosByAccountId,
        Map<String, BookView> booksBySymbol) {

    public static EngineSnapshot capture(OrderService orderService, ExecutionResult context) {
        Map<Long, OrderView> orders = new TreeMap<>();
        for (Long orderId : context.orderIds()) {
            orderService.getOrder(orderId).ifPresent(response -> orders.put(orderId, OrderView.from(response)));
        }

        List<LedgerEntryView> ledgerEntries = orderService.ledger().entries().stream()
                .map(LedgerEntryView::from)
                .toList();

        Map<Long, PortfolioView> portfolios = new TreeMap<>();
        for (Long accountId : context.accountIds()) {
            portfolios.put(accountId, PortfolioView.from(orderService.portfolio(accountId)));
        }

        Map<String, BookView> books = new TreeMap<>();
        for (String symbol : context.symbols()) {
            orderService.getOrderBook(symbol)
                    .ifPresent(book -> books.put(symbol, BookView.from(book)));
        }

        return new EngineSnapshot(orders, ledgerEntries, portfolios, books);
    }

    public record OrderView(
            long orderId, long accountId, String symbol, String side, String type,
            BigDecimal price, BigDecimal quantity, BigDecimal remainingQuantity, String status) {

        static OrderView from(OrderResponse response) {
            return new OrderView(
                    response.orderId(),
                    response.accountId(),
                    response.symbol(),
                    response.side().name(),
                    response.type().name(),
                    response.price(),
                    response.quantity(),
                    response.remainingQuantity(),
                    response.status().name());
        }
    }

    public record LedgerEntryView(
            long id, String accountCode, BigDecimal amount, String side, String currency, String narration) {

        static LedgerEntryView from(LedgerEntry entry) {
            return new LedgerEntryView(
                    entry.id(), entry.accountCode(), entry.amount(), entry.side().name(),
                    entry.currency(), entry.narration());
        }
    }

    public record PortfolioView(long accountId, BigDecimal cash, List<PositionView> positions,
                                 BigDecimal totalEquity) {

        static PortfolioView from(Portfolio portfolio) {
            List<PositionView> positions = portfolio.positions().stream()
                    .map(PositionView::from)
                    .sorted((a, b) -> a.symbol().compareTo(b.symbol()))
                    .toList();
            return new PortfolioView(portfolio.accountId(), portfolio.cash(), positions, portfolio.totalEquity());
        }
    }

    public record PositionView(String symbol, BigDecimal quantity, BigDecimal avgPrice,
                                BigDecimal realizedPnl, BigDecimal unrealizedPnl) {

        static PositionView from(Position position) {
            return new PositionView(position.symbol(), position.quantity(), position.avgPrice(),
                    position.realizedPnl(), position.unrealizedPnl());
        }
    }

    public record BookView(List<BookOrderView> bids, List<BookOrderView> asks) {

        static BookView from(OrderBookView view) {
            return new BookView(toBookOrderViews(view.bids()), toBookOrderViews(view.asks()));
        }

        private static List<BookOrderView> toBookOrderViews(List<OrderResponse> responses) {
            List<BookOrderView> views = new ArrayList<>(responses.size());
            for (OrderResponse response : responses) {
                views.add(new BookOrderView(response.orderId(), response.price(), response.remainingQuantity()));
            }
            return views;
        }
    }

    public record BookOrderView(long orderId, BigDecimal price, BigDecimal remainingQuantity) {
    }
}

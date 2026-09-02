package com.finex.api.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

import com.finex.common.domain.Order;
import com.finex.common.domain.Trade;
import com.finex.common.domain.enums.OrderStatus;
import com.finex.common.domain.enums.OrderType;
import com.finex.common.domain.enums.Side;
import com.finex.matching.MatchResult;
import com.finex.matching.MatchingEngine;
import com.finex.orderbook.OrderBook;
import com.finex.risk.AccountRiskState;
import com.finex.risk.RiskConfig;
import com.finex.risk.RiskEngine;
import com.finex.risk.RiskResult;

/**
 * Service that owns per-symbol {@link MatchingEngine} instances and exposes the trading
 * surface to the REST layer. In the baseline, engines are created lazily for any symbol
 * that receives an order; persistence of instruments/accounts is Phase 2/11+.
 *
 * <p>Phase 6 adds a baseline in-memory {@link RiskEngine} that validates every order before
 * it reaches the matching engine and updates account cash/positions when trades occur.
 */
@Service
public class OrderService {

    private static final BigDecimal DEFAULT_INITIAL_CASH = new BigDecimal("1000000");
    private static final BigDecimal DEFAULT_INITIAL_POSITION = BigDecimal.ZERO;

    private final Map<String, MatchingEngine> engines = new ConcurrentHashMap<>();
    private final Map<Long, Order> orderCache = new ConcurrentHashMap<>();
    private final AtomicLong orderSequence = new AtomicLong(0);

    private final RiskEngine riskEngine = new RiskEngine(RiskConfig.defaults());
    private final Map<Long, AccountRiskState> riskStates = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> lastTradePrices = new ConcurrentHashMap<>();

    public MatchResult submitOrder(OrderRequest request, Instant now) {
        validateRequest(request);

        MatchingEngine engine = engines.computeIfAbsent(request.symbol(), MatchingEngine::new);
        long orderId = orderSequence.incrementAndGet();
        long sequence = orderSequence.incrementAndGet();

        Order order = new Order(
                orderId,
                request.clientOrderId() == null ? ("cid-" + orderId) : request.clientOrderId(),
                request.accountId(),
                request.symbol(),
                request.side(),
                request.type(),
                request.price(),
                request.quantity(),
                request.quantity(),
                sequence,
                now,
                OrderStatus.OPEN);

        AccountRiskState state = riskState(request.accountId());
        RiskResult riskResult = riskEngine.validate(order, state, now, lastTradePrices.get(request.symbol()));
        if (!riskResult.accepted()) {
            Order rejected = order.rejected(now);
            orderCache.put(orderId, rejected);
            throw new OrderRejectedException(rejected, riskResult.reason());
        }

        MatchResult result = engine.placeOrder(order, now);
        orderCache.put(orderId, result.order());
        for (Trade trade : result.trades()) {
            lastTradePrices.put(request.symbol(), trade.price());
            applyTradeToRiskState(trade);
        }
        return result;
    }

    public Optional<OrderResponse> getOrder(long orderId) {
        Order cached = orderCache.get(orderId);
        if (cached != null) {
            // The order may have been partially filled by later trades while resting.
            // Check the live book first; if not there, use the cached final state.
            for (MatchingEngine engine : engines.values()) {
                Optional<Order> live = engine.orderBook().findOrder(orderId);
                if (live.isPresent()) {
                    return Optional.of(OrderResponse.from(orderId, live.get(), List.of(), true));
                }
            }
            return Optional.of(OrderResponse.from(orderId, cached, List.of(), false));
        }
        return Optional.empty();
    }

    public boolean cancelOrder(long orderId, Instant now) {
        for (MatchingEngine engine : engines.values()) {
            Optional<Order> live = engine.orderBook().findOrder(orderId);
            if (live.isPresent()) {
                boolean cancelled = engine.cancelOrder(orderId);
                if (cancelled) {
                    Order cancelledOrder = live.get().cancelled(now);
                    orderCache.put(orderId, cancelledOrder);
                    AccountRiskState state = riskStates.get(cancelledOrder.accountId());
                    if (state != null) {
                        riskEngine.onCancel(state, orderId);
                    }
                }
                return cancelled;
            }
        }
        return false;
    }

    public Optional<OrderBookView> getOrderBook(String symbol) {
        MatchingEngine engine = engines.get(symbol);
        if (engine == null) {
            return Optional.empty();
        }
        OrderBook book = engine.orderBook();
        return Optional.of(OrderBookView.from(symbol, book.getBids(), book.getAsks()));
    }

    private AccountRiskState riskState(long accountId) {
        return riskStates.computeIfAbsent(accountId, id ->
                new AccountRiskState(id, DEFAULT_INITIAL_CASH, DEFAULT_INITIAL_POSITION, riskEngine.config()));
    }

    private void applyTradeToRiskState(Trade trade) {
        AccountRiskState buyer = riskState(trade.buyerAccountId());
        AccountRiskState seller = riskState(trade.sellerAccountId());
        riskEngine.onTrade(buyer, trade.buyOrderId(), trade, Side.BUY);
        riskEngine.onTrade(seller, trade.sellOrderId(), trade, Side.SELL);
    }

    private static void validateRequest(OrderRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (request.symbol() == null || request.symbol().isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        if (request.side() == null) {
            throw new IllegalArgumentException("side must not be null");
        }
        if (request.type() == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (request.quantity() == null || request.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (request.type() == OrderType.LIMIT) {
            if (request.price() == null || request.price().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("LIMIT orders require a positive price");
            }
        }
        if (request.type() == OrderType.MARKET && request.price() != null) {
            throw new IllegalArgumentException("MARKET orders must not have a price");
        }
    }
}

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
import com.finex.eventlog.CancelOrderCommand;
import com.finex.eventlog.CommandHandler;
import com.finex.eventlog.CommandSerializer;
import com.finex.eventlog.Event;
import com.finex.eventlog.EventStore;
import com.finex.eventlog.InMemoryEventStore;
import com.finex.eventlog.SubmitOrderCommand;
import com.finex.marketdata.BookUpdate;
import com.finex.marketdata.BookUpdateFactory;
import com.finex.marketdata.ExecutionEvent;
import com.finex.marketdata.MarketDataPublisher;
import com.finex.marketdata.SimpleMarketDataPublisher;
import com.finex.marketdata.TradeEvent;
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
 *
 * <p>Phase 7 adds a {@link MarketDataPublisher} that emits {@link TradeEvent},
 * {@link ExecutionEvent}, and {@link BookUpdate} events on every book-changing action.
 *
 * <p>Phase 9 adds an append-only {@link EventStore} and makes the service replayable via
 * {@link CommandHandler}.
 */
@Service
public class OrderService implements CommandHandler {

    private static final BigDecimal DEFAULT_INITIAL_CASH = new BigDecimal("1000000");
    private static final BigDecimal DEFAULT_INITIAL_POSITION = BigDecimal.ZERO;

    private final Map<String, MatchingEngine> engines = new ConcurrentHashMap<>();
    private final Map<Long, Order> orderCache = new ConcurrentHashMap<>();
    private final AtomicLong orderSequence = new AtomicLong(0);

    private final RiskEngine riskEngine = new RiskEngine(RiskConfig.defaults());
    private final Map<Long, AccountRiskState> riskStates = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> lastTradePrices = new ConcurrentHashMap<>();

    private final MarketDataPublisher publisher = new SimpleMarketDataPublisher();
    private final EventStore eventStore;

    public OrderService() {
        this(new InMemoryEventStore());
    }

    public OrderService(EventStore eventStore) {
        if (eventStore == null) {
            throw new IllegalArgumentException("eventStore must not be null");
        }
        this.eventStore = eventStore;
    }

    public MatchResult submitOrder(OrderRequest request, Instant now) {
        validateRequest(request);
        SubmitOrderCommand command = new SubmitOrderCommand(
                request.accountId(),
                request.clientOrderId() == null ? ("cid-" + (orderSequence.get() + 1)) : request.clientOrderId(),
                request.symbol(),
                request.side(),
                request.type(),
                request.price(),
                request.quantity());
        eventStore.append(CommandSerializer.toEvent(command, now, 0L));
        return processSubmitOrder(command, now);
    }

    @Override
    public void submitOrder(SubmitOrderCommand command, Instant timestamp) {
        processSubmitOrder(command, timestamp);
    }

    private MatchResult processSubmitOrder(SubmitOrderCommand command, Instant now) {
        MatchingEngine engine = engines.computeIfAbsent(command.symbol(), MatchingEngine::new);
        long orderId = orderSequence.incrementAndGet();
        long sequence = orderSequence.incrementAndGet();

        Order order = new Order(
                orderId,
                command.clientOrderId(),
                command.accountId(),
                command.symbol(),
                command.side(),
                command.type(),
                command.price(),
                command.quantity(),
                command.quantity(),
                sequence,
                now,
                OrderStatus.OPEN);

        AccountRiskState state = riskState(command.accountId());
        RiskResult riskResult = riskEngine.validate(order, state, now, lastTradePrices.get(command.symbol()));
        if (!riskResult.accepted()) {
            Order rejected = order.rejected(now);
            orderCache.put(orderId, rejected);
            throw new OrderRejectedException(rejected, riskResult.reason());
        }

        MatchResult result = engine.placeOrder(order, now);
        orderCache.put(orderId, result.order());
        for (Trade trade : result.trades()) {
            lastTradePrices.put(command.symbol(), trade.price());
            applyTradeToRiskState(trade);
        }
        publishMatchEvents(command.symbol(), result, now);
        publishBookUpdate(command.symbol(), engine, now);
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
                CancelOrderCommand command = new CancelOrderCommand(live.get().accountId(), orderId);
                eventStore.append(CommandSerializer.toEvent(command, now, 0L));
                return doCancel(command, now);
            }
        }
        return false;
    }

    @Override
    public void cancelOrder(CancelOrderCommand command, Instant timestamp) {
        doCancel(command, timestamp);
    }

    private boolean doCancel(CancelOrderCommand command, Instant now) {
        for (MatchingEngine engine : engines.values()) {
            Optional<Order> live = engine.orderBook().findOrder(command.orderId());
            if (live.isPresent()) {
                if (live.get().accountId() != command.accountId()) {
                    return false;
                }
                boolean cancelled = engine.cancelOrder(command.orderId());
                if (cancelled) {
                    Order cancelledOrder = live.get().cancelled(now);
                    orderCache.put(command.orderId(), cancelledOrder);
                    AccountRiskState state = riskStates.get(cancelledOrder.accountId());
                    if (state != null) {
                        riskEngine.onCancel(state, command.orderId());
                    }
                    publishBookUpdate(cancelledOrder.symbol(), engine, now);
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

    /**
     * Exposes the market-data publisher so callers can subscribe to market events.
     */
    public MarketDataPublisher marketDataPublisher() {
        return publisher;
    }

    /**
     * Exposes the append-only event store.
     */
    public EventStore eventStore() {
        return eventStore;
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

    private void publishMatchEvents(String symbol, MatchResult result, Instant now) {
        if (result.trades().isEmpty()) {
            return;
        }
        MatchingEngine engine = engines.get(symbol);
        if (engine == null) {
            return;
        }

        Order finalIncoming = result.order();
        for (Trade trade : result.trades()) {
            publisher.publish(new TradeEvent(symbol, trade, now));

            long buyOrderId = trade.buyOrderId();
            long sellOrderId = trade.sellOrderId();
            publisher.publish(new ExecutionEvent(
                    buyOrderId,
                    trade.buyerAccountId(),
                    symbol,
                    trade,
                    orderStatusFor(engine, buyOrderId, finalIncoming),
                    now));
            publisher.publish(new ExecutionEvent(
                    sellOrderId,
                    trade.sellerAccountId(),
                    symbol,
                    trade,
                    orderStatusFor(engine, sellOrderId, finalIncoming),
                    now));
        }
    }

    private OrderStatus orderStatusFor(MatchingEngine engine, long orderId, Order finalIncoming) {
        if (orderId == finalIncoming.orderId()) {
            return finalIncoming.status();
        }
        return engine.orderBook().findOrder(orderId)
                .map(Order::status)
                .orElse(OrderStatus.FILLED);
    }

    private void publishBookUpdate(String symbol, MatchingEngine engine, Instant now) {
        BookUpdate update = BookUpdateFactory.from(symbol, engine.orderBook(), now);
        publisher.publish(update);
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

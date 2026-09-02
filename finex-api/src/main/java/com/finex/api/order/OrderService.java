package com.finex.api.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

import com.finex.clearing.ClearingResult;
import com.finex.clearing.ClearingService;
import com.finex.common.domain.Order;
import com.finex.common.domain.Trade;
import com.finex.common.domain.enums.OrderStatus;
import com.finex.common.domain.enums.OrderType;
import com.finex.common.domain.enums.Side;
import com.finex.eventlog.CancelOrderCommand;
import com.finex.eventlog.CommandHandler;
import com.finex.eventlog.CommandSerializer;
import com.finex.eventlog.EventStore;
import com.finex.eventlog.InMemoryEventStore;
import com.finex.eventlog.ReplayEngine;
import com.finex.eventlog.SubmitOrderCommand;
import com.finex.ledger.DebitCredit;
import com.finex.ledger.InMemoryLedger;
import com.finex.ledger.Ledger;
import com.finex.ledger.LedgerEntry;
import com.finex.marketdata.BookUpdate;
import com.finex.marketdata.BookUpdateFactory;
import com.finex.marketdata.ExecutionEvent;
import com.finex.marketdata.MarketDataPublisher;
import com.finex.marketdata.SimpleMarketDataPublisher;
import com.finex.marketdata.TradeEvent;
import com.finex.matching.MatchResult;
import com.finex.matching.MatchingEngine;
import com.finex.orderbook.OrderBook;
import com.finex.portfolio.Portfolio;
import com.finex.portfolio.PortfolioService;
import com.finex.risk.AccountRiskState;
import com.finex.risk.RiskConfig;
import com.finex.risk.RiskEngine;
import com.finex.risk.RiskResult;
import com.finex.shard.EngineShard;
import com.finex.shard.ShardCoordinator;

/**
 * Service that owns per-symbol {@link MatchingEngine} instances sharded by symbol and
 * exposes the trading surface to the REST layer. Persistence of instruments/accounts is
 * Phase 2/11+.
 *
 * <p>Phase 6 adds a baseline in-memory {@link RiskEngine} that validates every order before
 * it reaches the matching engine and updates account cash/positions when trades occur.
 *
 * <p>Phase 7 adds a {@link MarketDataPublisher} that emits {@link TradeEvent},
 * {@link ExecutionEvent}, and {@link BookUpdate} events on every book-changing action.
 *
 * <p>Phase 9 adds an append-only {@link EventStore} and makes the service replayable via
 * {@link CommandHandler}.
 *
 * <p>Phase 10 adds symbol sharding via {@link ShardCoordinator} so independent symbols can
 * be processed by independent {@link EngineShard}s.
 *
 * <p>Phase 11 adds a double-entry {@link Ledger} that posts balanced cash and asset entries
 * for every trade.
 *
 * <p>Phase 12 adds a {@link PortfolioService} for positions and P&L.
 *
 * <p>Phase 13 adds {@link ClearingService} to compute net cash obligations and fees.
 */
@Service
public class OrderService implements CommandHandler {

    private static final BigDecimal DEFAULT_INITIAL_CASH = new BigDecimal("1000000");
    private static final BigDecimal DEFAULT_INITIAL_POSITION = BigDecimal.ZERO;

    private final ShardCoordinator coordinator;
    private final Map<Long, Order> orderCache = new ConcurrentHashMap<>();
    private final AtomicLong orderSequence = new AtomicLong(0);

    private final RiskEngine riskEngine = new RiskEngine(RiskConfig.defaults());
    private final Map<Long, AccountRiskState> riskStates = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> lastTradePrices = new ConcurrentHashMap<>();

    private final MarketDataPublisher publisher = new SimpleMarketDataPublisher();
    private final EventStore eventStore;
    private final Ledger ledger = new InMemoryLedger();
    private final PortfolioService portfolioService = new PortfolioService();
    private final ClearingService clearingService = new ClearingService();

    public OrderService() {
        this(new InMemoryEventStore(), 1);
    }

    public OrderService(EventStore eventStore) {
        this(eventStore, 1);
    }

    public OrderService(EventStore eventStore, int shardCount) {
        if (eventStore == null) {
            throw new IllegalArgumentException("eventStore must not be null");
        }
        this.eventStore = eventStore;
        this.coordinator = new ShardCoordinator(shardCount);
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
        EngineShard shard = coordinator.shardFor(command.symbol());
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

        MatchResult result = shard.placeOrder(order, now);
        orderCache.put(orderId, result.order());
        for (Map.Entry<Long, Order> entry : result.updatedOrders().entrySet()) {
            orderCache.put(entry.getKey(), entry.getValue());
        }
        for (Trade trade : result.trades()) {
            lastTradePrices.put(command.symbol(), trade.price());
            applyTradeToRiskState(trade);
            ClearingResult clearing = clearingService.clear(trade, command.side());
            postTradeToLedger(trade, clearing, now);
            portfolioService.applyTrade(trade, trade.price());
            portfolioService.applyCashDelta(trade.buyerAccountId(), clearing.buyerCashDelta());
            portfolioService.applyCashDelta(trade.sellerAccountId(), clearing.sellerCashDelta());
            portfolioService.markToMarket(command.symbol(), trade.price());
        }
        publishMatchEvents(command.symbol(), shard, result, now);
        publishBookUpdate(command.symbol(), shard, now);
        return result;
    }

    public Optional<OrderResponse> getOrder(long orderId) {
        Order cached = orderCache.get(orderId);
        if (cached != null) {
            // The order may have been partially filled by later trades while resting.
            // Check the live book first; if not there, use the cached final state.
            for (EngineShard shard : coordinator.shards()) {
                Optional<Order> live = shard.findOrder(orderId);
                if (live.isPresent()) {
                    return Optional.of(OrderResponse.from(orderId, live.get(), List.of(), true));
                }
            }
            return Optional.of(OrderResponse.from(orderId, cached, List.of(), false));
        }
        return Optional.empty();
    }

    public boolean cancelOrder(long orderId, Instant now) {
        for (EngineShard shard : coordinator.shards()) {
            Optional<Order> live = shard.findOrder(orderId);
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
        for (EngineShard shard : coordinator.shards()) {
            Optional<Order> live = shard.findOrder(command.orderId());
            if (live.isPresent()) {
                if (live.get().accountId() != command.accountId()) {
                    return false;
                }
                boolean cancelled = shard.cancelOrder(command.orderId(), now);
                if (cancelled) {
                    Order cancelledOrder = live.get().cancelled(now);
                    orderCache.put(command.orderId(), cancelledOrder);
                    AccountRiskState state = riskStates.get(cancelledOrder.accountId());
                    if (state != null) {
                        riskEngine.onCancel(state, command.orderId());
                    }
                    publishBookUpdate(cancelledOrder.symbol(), shard, now);
                }
                return cancelled;
            }
        }
        return false;
    }

    public Optional<OrderBookView> getOrderBook(String symbol) {
        EngineShard shard = coordinator.shardFor(symbol);
        Optional<OrderBook> book = shard.orderBook(symbol);
        return book.map(b -> OrderBookView.from(symbol, b.getBids(), b.getAsks()));
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

    /**
     * Exposes the double-entry ledger.
     */
    public Ledger ledger() {
        return ledger;
    }

    /**
     * Returns the portfolio for an account.
     */
    public Portfolio portfolio(long accountId) {
        return portfolioService.portfolio(accountId);
    }

    /**
     * Replays all events from the event store into this service.
     */
    public void replay() {
        ReplayEngine.replay(eventStore, this);
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

    private void postTradeToLedger(Trade trade, ClearingResult clearing, Instant now) {
        String assetBuyer = assetAccount(trade.buyerAccountId(), trade.symbol());
        String cashBuyer = cashAccount(trade.buyerAccountId());
        String assetSeller = assetAccount(trade.sellerAccountId(), trade.symbol());
        String cashSeller = cashAccount(trade.sellerAccountId());
        String cashCurrency = "USD";
        String assetCurrency = trade.symbol();

        // Cash leg: seller receives net cash, buyer pays gross cash, fee accrual receives fees.
        List<LedgerEntry> cashEntries = new java.util.ArrayList<>(3);
        cashEntries.add(new LedgerEntry(0, now, cashSeller, clearing.sellerCashDelta().abs(), DebitCredit.DEBIT, cashCurrency,
                "Trade " + trade.tradeId() + " cash received"));
        cashEntries.add(new LedgerEntry(0, now, cashBuyer, clearing.buyerCashDelta().abs(), DebitCredit.CREDIT, cashCurrency,
                "Trade " + trade.tradeId() + " cash paid"));
        if (clearing.feeAccrued().compareTo(BigDecimal.ZERO) > 0) {
            cashEntries.add(new LedgerEntry(0, now, feeAccount(), clearing.feeAccrued(), DebitCredit.DEBIT, cashCurrency,
                    "Trade " + trade.tradeId() + " fee accrual"));
        }
        ledger.post(cashEntries);

        // Asset leg: buyer receives asset, seller delivers asset.
        ledger.post(List.of(
                new LedgerEntry(0, now, assetBuyer, trade.quantity(), DebitCredit.DEBIT, assetCurrency,
                        "Trade " + trade.tradeId() + " asset received"),
                new LedgerEntry(0, now, assetSeller, trade.quantity(), DebitCredit.CREDIT, assetCurrency,
                        "Trade " + trade.tradeId() + " asset delivered")));
    }

    private static String cashAccount(long accountId) {
        return "CASH." + accountId;
    }

    private static String assetAccount(long accountId, String symbol) {
        return "ASSET." + symbol + "." + accountId;
    }

    private static String feeAccount() {
        return "FEE.ACCRUAL";
    }

    private void publishMatchEvents(String symbol, EngineShard shard, MatchResult result, Instant now) {
        if (result.trades().isEmpty()) {
            return;
        }
        MatchingEngine engine = shard.matchingEngine(symbol);

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

    private void publishBookUpdate(String symbol, EngineShard shard, Instant now) {
        shard.orderBook(symbol).ifPresent(book -> {
            BookUpdate update = BookUpdateFactory.from(symbol, book, now);
            publisher.publish(update);
        });
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

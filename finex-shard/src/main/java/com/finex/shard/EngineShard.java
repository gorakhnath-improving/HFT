package com.finex.shard;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.finex.common.domain.Order;
import com.finex.matching.MatchResult;
import com.finex.matching.MatchingEngine;
import com.finex.orderbook.OrderBook;

/**
 * A single matching-engine shard. Each shard owns a set of per-symbol {@link MatchingEngine}
 * instances. Commands for a symbol are always routed to the same shard.
 */
public class EngineShard {

    private final int shardId;
    private final Map<String, MatchingEngine> engines = new ConcurrentHashMap<>();

    public EngineShard(int shardId) {
        if (shardId < 0) {
            throw new IllegalArgumentException("shardId must not be negative");
        }
        this.shardId = shardId;
    }

    public int shardId() {
        return shardId;
    }

    public MatchResult placeOrder(Order order, Instant now) {
        if (order == null) {
            throw new IllegalArgumentException("order must not be null");
        }
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }
        MatchingEngine engine = engines.computeIfAbsent(order.symbol(), MatchingEngine::new);
        return engine.placeOrder(order, now);
    }

    public boolean cancelOrder(long orderId, Instant now) {
        for (MatchingEngine engine : engines.values()) {
            if (engine.orderBook().findOrder(orderId).isPresent()) {
                return engine.cancelOrder(orderId);
            }
        }
        return false;
    }

    public Optional<Order> findOrder(long orderId) {
        for (MatchingEngine engine : engines.values()) {
            Optional<Order> live = engine.orderBook().findOrder(orderId);
            if (live.isPresent()) {
                return live;
            }
        }
        return Optional.empty();
    }

    public Optional<OrderBook> orderBook(String symbol) {
        MatchingEngine engine = engines.get(symbol);
        return engine == null ? Optional.empty() : Optional.of(engine.orderBook());
    }

    /**
     * Returns the {@link MatchingEngine} for a symbol, creating it if necessary.
     */
    public MatchingEngine matchingEngine(String symbol) {
        return engines.computeIfAbsent(symbol, MatchingEngine::new);
    }
}

package com.finex.loadgenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.finex.api.order.OrderRequest;
import com.finex.api.order.OrderService;
import com.finex.common.domain.enums.OrderType;
import com.finex.common.domain.enums.Side;

/**
 * Deterministic, single-threaded load generator that submits orders to an {@link OrderService}
 * and reports throughput and latency. It is intended for functional validation and as a harness
 * for future JMH/performance benchmarks.
 */
public class LoadGenerator {

    private final OrderService orderService;

    public LoadGenerator(OrderService orderService) {
        if (orderService == null) {
            throw new IllegalArgumentException("orderService must not be null");
        }
        this.orderService = orderService;
    }

    /**
     * Runs the configured workload once and returns a summary. Prices alternate so that each
     * BUY order is priced above the corresponding SELL order, guaranteeing some trades while
     * leaving remaining depth on the book.
     */
    public LoadResult run(LoadConfig config) {
        List<String> symbols = config.symbols();
        List<Long> accounts = config.accounts();

        long maxLatency = 0;
        long totalLatency = 0;
        Instant timestamp = config.startTime();
        int totalTrades = 0;

        long start = System.nanoTime();
        int submitted = 0;

        for (String symbol : symbols) {
            for (int i = 0; i < config.ordersPerSymbol(); i++) {
                Side side = (i % 2 == 0) ? Side.SELL : Side.BUY;
                long account = accounts.get(i % accounts.size());

                BigDecimal amount = config.priceJitter().multiply(BigDecimal.valueOf(i));
                BigDecimal price = side == Side.SELL
                        ? config.basePrice().subtract(amount)
                        : config.basePrice().add(amount);
                if (price.compareTo(BigDecimal.ONE) < 0) {
                    price = BigDecimal.ONE;
                }

                String clientOrderId = "lg-" + symbol + "-" + i;
                OrderRequest request = new OrderRequest(
                        clientOrderId, symbol, side, OrderType.LIMIT,
                        price, config.quantity(), account);

                long t0 = System.nanoTime();
                var result = orderService.submitOrder(request, timestamp);
                long t1 = System.nanoTime();

                totalTrades += result.trades().size();
                long latency = t1 - t0;
                totalLatency += latency;
                if (latency > maxLatency) {
                    maxLatency = latency;
                }

                timestamp = timestamp.plusNanos(1_000_000); // 1 ms between orders
                submitted++;
            }
        }

        long elapsed = System.nanoTime() - start;
        double throughput = submitted / (elapsed / 1_000_000_000.0);
        double avgLatency = submitted == 0 ? 0 : totalLatency / (double) submitted;

        return new LoadResult(submitted, totalTrades, elapsed, throughput, avgLatency, maxLatency);
    }
}

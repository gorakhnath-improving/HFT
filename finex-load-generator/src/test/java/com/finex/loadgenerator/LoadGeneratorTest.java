package com.finex.loadgenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.finex.api.order.OrderService;

import static org.assertj.core.api.Assertions.assertThat;

class LoadGeneratorTest {

    @Test
    void loadRunProducesTradesAndLeavesOrderBook() {
        OrderService service = new OrderService();
        LoadGenerator generator = new LoadGenerator(service);
        LoadConfig config = new LoadConfig(
                List.of("BTC-USD"),
                List.of(100L, 200L),
                20,
                new BigDecimal("50000"),
                new BigDecimal("0.01"),
                new BigDecimal("100"),
                1L,
                Instant.parse("2026-01-01T00:00:00Z"));

        LoadResult result = generator.run(config);

        assertThat(result.submitted()).isEqualTo(20);
        assertThat(result.trades()).isGreaterThan(0);
        assertThat(service.getOrderBook("BTC-USD")).isPresent();

        // Sanity: buyer and seller portfolios were updated.
        assertThat(service.portfolio(100L).positions()).isNotEmpty();
        assertThat(service.portfolio(200L).positions()).isNotEmpty();
    }
}

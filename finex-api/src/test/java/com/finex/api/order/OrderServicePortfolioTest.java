package com.finex.api.order;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.finex.common.domain.enums.OrderType;
import com.finex.common.domain.enums.Side;
import com.finex.portfolio.Portfolio;

import static org.assertj.core.api.Assertions.assertThat;

class OrderServicePortfolioTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void tradeUpdatesPortfolio() {
        OrderService service = new OrderService();

        service.submitOrder(new OrderRequest(
                "cid-s1", "BTC-USD", Side.SELL, OrderType.LIMIT,
                new BigDecimal("50000"), new BigDecimal("1"), 200L), NOW);
        service.submitOrder(new OrderRequest(
                "cid-b1", "BTC-USD", Side.BUY, OrderType.LIMIT,
                new BigDecimal("50000"), new BigDecimal("1"), 100L), NOW);

        Portfolio buyer = service.portfolio(100L);
        assertThat(buyer.cash()).isEqualByComparingTo(new BigDecimal("949950")); // 1M - 50k - 50 fee
        assertThat(buyer.positions()).hasSize(1);
        assertThat(buyer.positions().get(0).quantity()).isEqualByComparingTo(new BigDecimal("1"));
        assertThat(buyer.positions().get(0).avgPrice()).isEqualByComparingTo(new BigDecimal("50000"));
        assertThat(buyer.totalEquity()).isEqualByComparingTo(new BigDecimal("949950"));

        Portfolio seller = service.portfolio(200L);
        assertThat(seller.cash()).isEqualByComparingTo(new BigDecimal("1050000")); // 1M + 50k
        assertThat(seller.positions().get(0).quantity()).isEqualByComparingTo(new BigDecimal("-1"));
    }
}

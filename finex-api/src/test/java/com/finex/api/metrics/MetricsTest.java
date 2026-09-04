package com.finex.api.metrics;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.finex.api.order.OrderRejectedException;
import com.finex.api.order.OrderRequest;
import com.finex.api.order.OrderService;
import com.finex.common.domain.enums.OrderType;
import com.finex.common.domain.enums.Side;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricsTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void orderLifecycleMetricsAreRecorded() {
        OrderService service = new OrderService();

        service.submitOrder(new OrderRequest(
                "sell-1", "BTC-USD", Side.SELL, OrderType.LIMIT,
                new BigDecimal("50000"), BigDecimal.ONE, 100L), NOW);
        service.submitOrder(new OrderRequest(
                "buy-1", "BTC-USD", Side.BUY, OrderType.LIMIT,
                new BigDecimal("49000"), BigDecimal.ONE, 200L), NOW);

        assertThat(service.metricsService().submittedCount()).isEqualTo(2);
        assertThat(service.metricsService().tradeCount()).isEqualTo(0);

        service.cancelOrder(1L, NOW);
        assertThat(service.metricsService().cancelledCount()).isEqualTo(1);
    }

    @Test
    void rejectedOrderMetricIsRecorded() {
        OrderService service = new OrderService();

        assertThatThrownBy(() -> service.submitOrder(new OrderRequest(
                "big", "BTC-USD", Side.BUY, OrderType.LIMIT,
                new BigDecimal("50000"), new BigDecimal("1000"), 200L), NOW))
                .isInstanceOf(OrderRejectedException.class);

        assertThat(service.metricsService().rejectedCount()).isEqualTo(1);
    }

    @Test
    void tradeMetricIsRecorded() {
        OrderService service = new OrderService();

        service.submitOrder(new OrderRequest(
                "sell-1", "BTC-USD", Side.SELL, OrderType.LIMIT,
                new BigDecimal("50000"), BigDecimal.ONE, 100L), NOW);
        service.submitOrder(new OrderRequest(
                "buy-1", "BTC-USD", Side.BUY, OrderType.LIMIT,
                new BigDecimal("50000"), BigDecimal.ONE, 200L), NOW);

        assertThat(service.metricsService().submittedCount()).isEqualTo(2);
        assertThat(service.metricsService().tradeCount()).isEqualTo(1);
    }
}

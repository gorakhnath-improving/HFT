package com.finex.api.order;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.finex.common.domain.enums.OrderStatus;
import com.finex.common.domain.enums.OrderType;
import com.finex.common.domain.enums.Side;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Failure/chaos-style tests for {@link OrderService}: invalid input, risk rejections, and
 * rejected market orders.
 */
class OrderServiceFailureTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void rejectsNullRequest() {
        OrderService service = new OrderService();
        assertThatThrownBy(() -> service.submitOrder((OrderRequest) null, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("request must not be null");
    }

    @Test
    void rejectsBlankSymbol() {
        OrderService service = new OrderService();
        assertThatThrownBy(() -> service.submitOrder(new OrderRequest(
                "cid-1", "", Side.BUY, OrderType.LIMIT,
                new BigDecimal("50000"), BigDecimal.ONE, 100L), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("symbol must not be blank");
    }

    @Test
    void rejectsNonPositiveQuantity() {
        OrderService service = new OrderService();
        assertThatThrownBy(() -> service.submitOrder(new OrderRequest(
                "cid-1", "BTC-USD", Side.BUY, OrderType.LIMIT,
                new BigDecimal("50000"), BigDecimal.ZERO, 100L), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity must be positive");
    }

    @Test
    void limitOrderRequiresPositivePrice() {
        OrderService service = new OrderService();
        assertThatThrownBy(() -> service.submitOrder(new OrderRequest(
                "cid-1", "BTC-USD", Side.BUY, OrderType.LIMIT,
                null, BigDecimal.ONE, 100L), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LIMIT orders require a positive price");
    }

    @Test
    void marketOrderMustNotHavePrice() {
        OrderService service = new OrderService();
        assertThatThrownBy(() -> service.submitOrder(new OrderRequest(
                "cid-1", "BTC-USD", Side.BUY, OrderType.MARKET,
                new BigDecimal("50000"), BigDecimal.ONE, 100L), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MARKET orders must not have a price");
    }

    @Test
    void rejectsOrderExceedingCashLimits() {
        OrderService service = new OrderService();

        // Default max order notional is 500,000; this order's notional is 50,000,000.
        assertThatThrownBy(() -> service.submitOrder(new OrderRequest(
                "cid-1", "BTC-USD", Side.BUY, OrderType.LIMIT,
                new BigDecimal("50000"), new BigDecimal("1000"), 200L), NOW))
                .isInstanceOf(OrderRejectedException.class)
                .hasMessageContaining("max order notional");

        assertThat(service.metricsService().rejectedCount()).isEqualTo(1);
    }

    @Test
    void cancelledOrderCanNoLongerBeLookedUpAsLive() {
        OrderService service = new OrderService();

        service.submitOrder(new OrderRequest(
                "cid-1", "BTC-USD", Side.SELL, OrderType.LIMIT,
                new BigDecimal("50000"), BigDecimal.ONE, 100L), NOW);

        boolean cancelled = service.cancelOrder(1L, NOW);
        assertThat(cancelled).isTrue();
        assertThat(service.getOrder(1L))
                .isPresent()
                .hasValueSatisfying(r -> assertThat(r.status()).isEqualTo(OrderStatus.CANCELLED));
        assertThat(service.metricsService().cancelledCount()).isEqualTo(1);
    }
}

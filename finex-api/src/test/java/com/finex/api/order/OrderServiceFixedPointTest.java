package com.finex.api.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.finex.common.domain.enums.OrderType;
import com.finex.common.domain.enums.Side;

class OrderServiceFixedPointTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void rejectsUnsupportedPrecisionBeforeAppendingEvent() {
        OrderService service = new OrderService(OrderService.NumericMode.FIXED_POINT);
        OrderRequest request = new OrderRequest("cid", "BTC-USD", Side.BUY, OrderType.LIMIT,
                new BigDecimal("100.00001"), BigDecimal.ONE, 1);

        assertThatThrownBy(() -> service.submitOrder(request, NOW))
                .isInstanceOf(ArithmeticException.class);
        assertThat(service.eventStore().size()).isZero();
    }

    @Test
    void fixedPointExecutionReplaysIntoFixedPointMode() {
        OrderService direct = new OrderService(OrderService.NumericMode.FIXED_POINT);
        direct.submitOrder(request("sell", Side.SELL, "100", "1", 2), NOW);
        direct.submitOrder(request("buy", Side.BUY, "100", "1", 1), NOW.plusMillis(1));

        OrderService replayed = new OrderService(direct.eventStore(), 1, OrderService.NumericMode.FIXED_POINT);
        replayed.replay();

        assertThat(replayed.ledger().entries()).isEqualTo(direct.ledger().entries());
        assertThat(replayed.portfolio(1)).isEqualTo(direct.portfolio(1));
        assertThat(replayed.portfolio(2)).isEqualTo(direct.portfolio(2));
    }

    private static OrderRequest request(String clientOrderId, Side side, String price, String quantity,
                                        long accountId) {
        return new OrderRequest(clientOrderId, "BTC-USD", side, OrderType.LIMIT,
                new BigDecimal(price), new BigDecimal(quantity), accountId);
    }
}

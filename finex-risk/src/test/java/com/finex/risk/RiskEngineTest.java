package com.finex.risk;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.finex.common.domain.Order;
import com.finex.common.domain.Trade;
import com.finex.common.domain.enums.OrderStatus;
import com.finex.common.domain.enums.OrderType;
import com.finex.common.domain.enums.Side;

import static org.assertj.core.api.Assertions.assertThat;

class RiskEngineTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final RiskConfig CONFIG = RiskConfig.defaults();

    @Test
    void acceptsValidLimitBuy() {
        RiskEngine engine = new RiskEngine(CONFIG);
        AccountRiskState state = new AccountRiskState(1L, new BigDecimal("500000"), BigDecimal.ZERO, CONFIG);
        Order order = order(1L, Side.BUY, OrderType.LIMIT, new BigDecimal("50000"), new BigDecimal("1"));

        RiskResult result = engine.validate(order, state, NOW, new BigDecimal("50000"));

        assertThat(result.accepted()).isTrue();
        assertThat(state.availableCash()).isEqualTo(new BigDecimal("450000"));
        assertThat(state.projectedPosition()).isEqualTo(new BigDecimal("1"));
    }

    @Test
    void rejectsOrderExceedingMaxQuantity() {
        RiskEngine engine = new RiskEngine(CONFIG);
        AccountRiskState state = new AccountRiskState(1L, new BigDecimal("500000"), BigDecimal.ZERO, CONFIG);
        Order order = order(1L, Side.SELL, OrderType.LIMIT, new BigDecimal("1"), new BigDecimal("2000"));

        RiskResult result = engine.validate(order, state, NOW, new BigDecimal("1"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.reason()).contains("max order quantity");
    }

    @Test
    void rejectsOrderExceedingMaxNotional() {
        RiskEngine engine = new RiskEngine(CONFIG);
        AccountRiskState state = new AccountRiskState(1L, new BigDecimal("500000"), BigDecimal.ZERO, CONFIG);
        Order order = order(1L, Side.BUY, OrderType.LIMIT, new BigDecimal("50000"), new BigDecimal("20"));

        RiskResult result = engine.validate(order, state, NOW, new BigDecimal("50000"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.reason()).contains("max order notional");
    }

    @Test
    void rejectsInsufficientCash() {
        RiskEngine engine = new RiskEngine(CONFIG);
        AccountRiskState state = new AccountRiskState(1L, new BigDecimal("10000"), BigDecimal.ZERO, CONFIG);
        Order order = order(1L, Side.BUY, OrderType.LIMIT, new BigDecimal("50000"), new BigDecimal("1"));

        RiskResult result = engine.validate(order, state, NOW, new BigDecimal("50000"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.reason()).contains("insufficient available cash");
    }

    @Test
    void rejectsPositionLimit() {
        RiskEngine engine = new RiskEngine(CONFIG);
        AccountRiskState state = new AccountRiskState(1L, new BigDecimal("500000"), BigDecimal.ZERO, CONFIG);
        Order order = order(1L, Side.SELL, OrderType.LIMIT, new BigDecimal("1"), new BigDecimal("200"));

        RiskResult result = engine.validate(order, state, NOW, new BigDecimal("1"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.reason()).contains("max position");
    }

    @Test
    void rejectsPriceOutsideCollar() {
        RiskEngine engine = new RiskEngine(CONFIG);
        AccountRiskState state = new AccountRiskState(1L, new BigDecimal("500000"), BigDecimal.ZERO, CONFIG);
        Order order = order(1L, Side.BUY, OrderType.LIMIT, new BigDecimal("75000"), new BigDecimal("1"));

        RiskResult result = engine.validate(order, state, NOW, new BigDecimal("50000"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.reason()).contains("collar");
    }

    @Test
    void rejectsMarketOrderWithoutLastTradePrice() {
        RiskEngine engine = new RiskEngine(CONFIG);
        AccountRiskState state = new AccountRiskState(1L, new BigDecimal("500000"), BigDecimal.ZERO, CONFIG);
        Order order = order(1L, Side.BUY, OrderType.MARKET, null, new BigDecimal("1"));

        RiskResult result = engine.validate(order, state, NOW, null);

        assertThat(result.accepted()).isFalse();
        assertThat(result.reason()).contains("last trade price");
    }

    @Test
    void appliesTradeAndUpdatesCashAndPosition() {
        RiskEngine engine = new RiskEngine(CONFIG);
        AccountRiskState state = new AccountRiskState(1L, new BigDecimal("500000"), BigDecimal.ZERO, CONFIG);
        Order order = order(1L, Side.BUY, OrderType.LIMIT, new BigDecimal("50000"), new BigDecimal("2"));

        engine.validate(order, state, NOW, new BigDecimal("50000"));
        Trade trade = trade(1L, 2L, new BigDecimal("50000"), new BigDecimal("1"));
        engine.onTrade(state, 1L, trade, Side.BUY);

        assertThat(state.cash()).isEqualTo(new BigDecimal("450000"));
        assertThat(state.position()).isEqualTo(BigDecimal.ONE);
        assertThat(state.reservedCash()).isEqualTo(new BigDecimal("50000")); // remaining 1
        assertThat(state.availableCash()).isEqualTo(new BigDecimal("400000"));
    }

    @Test
    void cancelReleasesReservation() {
        RiskEngine engine = new RiskEngine(CONFIG);
        AccountRiskState state = new AccountRiskState(1L, new BigDecimal("500000"), BigDecimal.ZERO, CONFIG);
        Order order = order(1L, Side.BUY, OrderType.LIMIT, new BigDecimal("50000"), new BigDecimal("1"));

        engine.validate(order, state, NOW, new BigDecimal("50000"));
        assertThat(state.reservedCash()).isEqualTo(new BigDecimal("50000"));

        engine.onCancel(state, 1L);

        assertThat(state.reservedCash()).isEqualTo(BigDecimal.ZERO);
        assertThat(state.availableCash()).isEqualTo(new BigDecimal("500000"));
    }

    @Test
    void enforcesRateLimit() {
        RiskEngine engine = new RiskEngine(new RiskConfig(
                new BigDecimal("1000"), new BigDecimal("500000"), new BigDecimal("100"),
                new BigDecimal("500000"), BigDecimal.ZERO, 2));
        AccountRiskState state = new AccountRiskState(1L, new BigDecimal("500000"), BigDecimal.ZERO, CONFIG);

        Order order1 = order(1L, Side.SELL, OrderType.LIMIT, new BigDecimal("1"), new BigDecimal("1"));
        Order order2 = order(2L, Side.SELL, OrderType.LIMIT, new BigDecimal("1"), new BigDecimal("1"));
        Order order3 = order(3L, Side.SELL, OrderType.LIMIT, new BigDecimal("1"), new BigDecimal("1"));

        assertThat(engine.validate(order1, state, NOW, new BigDecimal("1")).accepted()).isTrue();
        assertThat(engine.validate(order2, state, NOW.plusMillis(100), new BigDecimal("1")).accepted()).isTrue();
        RiskResult result = engine.validate(order3, state, NOW.plusMillis(200), new BigDecimal("1"));
        assertThat(result.accepted()).isFalse();
        assertThat(result.reason()).contains("rate limit");
    }

    private static Order order(long id, Side side, OrderType type, BigDecimal price, BigDecimal qty) {
        return new Order(
                id, "cid-" + id, 100L, "BTC-USD", side, type,
                price, qty, qty, id, NOW, OrderStatus.OPEN);
    }

    private static Trade trade(long buyOrderId, long sellOrderId, BigDecimal price, BigDecimal qty) {
        return new Trade(
                1L, buyOrderId, sellOrderId, "BTC-USD", price, qty,
                NOW, 100L, 200L, 1L);
    }
}

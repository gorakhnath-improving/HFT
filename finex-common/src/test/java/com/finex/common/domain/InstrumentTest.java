package com.finex.common.domain;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.finex.common.domain.enums.AccountStatus;
import com.finex.common.domain.enums.DebitCredit;
import com.finex.common.domain.enums.EntryType;
import com.finex.common.domain.enums.InstrumentStatus;
import com.finex.common.domain.enums.LedgerAccountType;
import com.finex.common.domain.enums.OrderStatus;
import com.finex.common.domain.enums.OrderType;
import com.finex.common.domain.enums.Side;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstrumentTest {

    @Test
    void createsValidInstrument() {
        Instrument instrument = new Instrument(
                "BTC-USD", "BTC", "USD",
                new BigDecimal("0.01"), new BigDecimal("0.0001"),
                InstrumentStatus.ACTIVE);

        assertThat(instrument.symbol()).isEqualTo("BTC-USD");
        assertThat(instrument.tickSize()).isEqualTo(new BigDecimal("0.01"));
        assertThat(instrument.status()).isEqualTo(InstrumentStatus.ACTIVE);
    }

    @Test
    void rejectsBlankSymbol() {
        assertThatThrownBy(() -> new Instrument(
                "", "BTC", "USD", BigDecimal.ONE, BigDecimal.ONE, InstrumentStatus.ACTIVE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("symbol");
    }

    @Test
    void rejectsNonPositiveTickSize() {
        assertThatThrownBy(() -> new Instrument(
                "BTC-USD", "BTC", "USD", BigDecimal.ZERO, BigDecimal.ONE, InstrumentStatus.ACTIVE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tickSize");
    }

    @Test
    void createsValidOrder() {
        Instant now = Instant.now();
        Order order = new Order(
                1L, "cid-1", 100L, "BTC-USD", Side.BUY, OrderType.LIMIT,
                new BigDecimal("50000"), new BigDecimal("1"), new BigDecimal("1"),
                1L, now, OrderStatus.OPEN);

        assertThat(order.side()).isEqualTo(Side.BUY);
        assertThat(order.status()).isEqualTo(OrderStatus.OPEN);
    }

    @Test
    void rejectsLimitOrderWithoutPrice() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> new Order(
                1L, "cid-1", 100L, "BTC-USD", Side.BUY, OrderType.LIMIT,
                null, new BigDecimal("1"), new BigDecimal("1"),
                1L, now, OrderStatus.OPEN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LIMIT orders require a positive price");
    }

    @Test
    void rejectsNegativeRemainingQuantity() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> new Order(
                1L, "cid-1", 100L, "BTC-USD", Side.BUY, OrderType.MARKET,
                null, new BigDecimal("1"), new BigDecimal("-1"),
                1L, now, OrderStatus.OPEN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("remainingQuantity");
    }

    @Test
    void withFillProducesCorrectlyUpdatedOrder() {
        Instant now = Instant.now();
        Order order = new Order(
                1L, "cid-1", 100L, "BTC-USD", Side.BUY, OrderType.LIMIT,
                new BigDecimal("50000"), new BigDecimal("2"), new BigDecimal("2"),
                1L, now, OrderStatus.OPEN);

        Instant fillTime = now.plusMillis(100);
        Order filled = order.withFill(BigDecimal.ONE, OrderStatus.PARTIALLY_FILLED, fillTime);

        assertThat(filled.remainingQuantity()).isEqualTo(BigDecimal.ONE);
        assertThat(filled.status()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(filled.timestamp()).isEqualTo(fillTime);
    }

    @Test
    void createsValidTrade() {
        Instant now = Instant.now();
        Trade trade = new Trade(
                1L, 100L, 200L, "BTC-USD",
                new BigDecimal("50000"), new BigDecimal("0.5"),
                now, 100L, 200L, 1L);

        assertThat(trade.price()).isEqualTo(new BigDecimal("50000"));
        assertThat(trade.quantity()).isEqualTo(new BigDecimal("0.5"));
    }

    @Test
    void balanceTotalIsSum() {
        Balance balance = new Balance(
                100L, "USD", new BigDecimal("1000"), new BigDecimal("200"));

        assertThat(balance.total()).isEqualTo(new BigDecimal("1200"));
    }

    @Test
    void rejectsNegativeBalance() {
        assertThatThrownBy(() -> new Balance(
                100L, "USD", new BigDecimal("-1"), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("available");
    }

    @Test
    void positionMarketValueAndPnlAreComputed() {
        Position position = new Position(
                100L, "BTC-USD", new BigDecimal("2"),
                new BigDecimal("40000"), new BigDecimal("100"), new BigDecimal("-200"));

        assertThat(position.marketValue(new BigDecimal("39000"))).isEqualTo(new BigDecimal("78000"));
        assertThat(position.totalPnl()).isEqualTo(new BigDecimal("-100"));
    }

    @Test
    void ledgerEntryRequiresPositiveAmount() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> new LedgerEntry(
                1L, 10L, BigDecimal.ZERO, DebitCredit.DEBIT,
                EntryType.TRADE, "ref-1", "group-1", now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void accountHoldsStatus() {
        Account account = new Account(1L, 1L, AccountStatus.ACTIVE, Instant.now());
        assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void userValidatesBlankUsername() {
        assertThatThrownBy(() -> new User(
                1L, "", "test@example.com", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("username");
    }

    @Test
    void ledgerAccountValidatesName() {
        assertThatThrownBy(() -> new LedgerAccount(
                1L, 1L, "USD", LedgerAccountType.CASH, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }
}

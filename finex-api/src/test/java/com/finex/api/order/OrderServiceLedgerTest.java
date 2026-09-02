package com.finex.api.order;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.finex.common.domain.enums.OrderType;
import com.finex.common.domain.enums.Side;

import static org.assertj.core.api.Assertions.assertThat;

class OrderServiceLedgerTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void tradeCreatesBalancedLedgerPostings() {
        OrderService service = new OrderService();

        service.submitOrder(new OrderRequest(
                "cid-s1", "BTC-USD", Side.SELL, OrderType.LIMIT,
                new BigDecimal("50000"), new BigDecimal("1"), 200L), NOW);
        service.submitOrder(new OrderRequest(
                "cid-b1", "BTC-USD", Side.BUY, OrderType.LIMIT,
                new BigDecimal("50000"), new BigDecimal("1"), 100L), NOW);

        // Cash leg: seller cash +50000, buyer cash -50000.
        assertThat(service.ledger().balance("CASH.200")).isEqualTo(new BigDecimal("50000"));
        assertThat(service.ledger().balance("CASH.100")).isEqualTo(new BigDecimal("-50000"));

        // Asset leg: buyer asset +1, seller asset -1.
        assertThat(service.ledger().balance("ASSET.BTC-USD.100")).isEqualTo(new BigDecimal("1"));
        assertThat(service.ledger().balance("ASSET.BTC-USD.200")).isEqualTo(new BigDecimal("-1"));

        // Total ledger entries: 2 postings * 2 entries = 4.
        assertThat(service.ledger().entries()).hasSize(4);
    }
}

package com.finex.settlement;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.finex.clearing.ClearingService;
import com.finex.common.domain.Trade;
import com.finex.common.domain.enums.Side;
import com.finex.ledger.InMemoryLedger;
import com.finex.ledger.Ledger;
import com.finex.portfolio.Portfolio;
import com.finex.portfolio.PortfolioService;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void settleUpdatesLedgerAndPortfolio() {
        Ledger ledger = new InMemoryLedger();
        PortfolioService portfolioService = new PortfolioService();
        SettlementService settlement = new SettlementService(new ClearingService(), ledger, portfolioService);

        Trade trade = new Trade(1L, 1L, 2L, "BTC-USD",
                new BigDecimal("50000"), new BigDecimal("1"), NOW, 100L, 200L, 1L);
        settlement.settle(trade, Side.BUY, NOW, trade.price());

        assertThat(ledger.balance("CASH.100")).isEqualByComparingTo(new BigDecimal("-50050"));
        assertThat(ledger.balance("CASH.200")).isEqualByComparingTo(new BigDecimal("50000"));
        assertThat(ledger.balance("FEE.ACCRUAL")).isEqualByComparingTo(new BigDecimal("50"));

        Portfolio buyer = portfolioService.portfolio(100L);
        assertThat(buyer.positions().get(0).quantity()).isEqualByComparingTo(new BigDecimal("1"));
        assertThat(buyer.cash()).isEqualByComparingTo(new BigDecimal("949950"));
    }
}

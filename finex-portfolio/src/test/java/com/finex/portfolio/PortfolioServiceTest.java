package com.finex.portfolio;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.finex.common.domain.Trade;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void buyIncreasesPositionAndCashDecreases() {
        PortfolioService service = new PortfolioService();
        Trade trade = new Trade(1L, 1L, 2L, "BTC-USD",
                new BigDecimal("50000"), new BigDecimal("1"), NOW, 100L, 200L, 1L);

        service.applyTrade(trade, trade.price());
        service.applyCashDelta(trade.buyerAccountId(), new BigDecimal("-50000"));
        service.applyCashDelta(trade.sellerAccountId(), new BigDecimal("50000"));

        Portfolio buyer = service.portfolio(100L);
        assertThat(buyer.positions()).hasSize(1);
        assertThat(buyer.positions().get(0).quantity()).isEqualTo(new BigDecimal("1"));
        assertThat(buyer.positions().get(0).avgPrice()).isEqualTo(new BigDecimal("50000"));
        assertThat(buyer.cash()).isEqualTo(new BigDecimal("950000")); // 1M - 50k
        assertThat(buyer.totalEquity()).isEqualTo(new BigDecimal("950000")); // cash + no unrealized PnL

        Portfolio seller = service.portfolio(200L);
        assertThat(seller.positions().get(0).quantity()).isEqualTo(new BigDecimal("-1"));
        assertThat(seller.cash()).isEqualTo(new BigDecimal("1050000")); // 1M + 50k
    }

    @Test
    void realizedPnlOnClosingTrade() {
        PortfolioService service = new PortfolioService();

        Trade open = new Trade(1L, 1L, 2L, "BTC-USD",
                new BigDecimal("50000"), new BigDecimal("1"), NOW, 100L, 200L, 1L);
        service.applyTrade(open, open.price());
        service.applyCashDelta(open.buyerAccountId(), new BigDecimal("-50000"));
        service.applyCashDelta(open.sellerAccountId(), new BigDecimal("50000"));

        Trade close = new Trade(2L, 3L, 1L, "BTC-USD",
                new BigDecimal("55000"), new BigDecimal("1"), NOW, 300L, 100L, 2L);
        service.applyTrade(close, close.price());
        service.applyCashDelta(close.buyerAccountId(), new BigDecimal("-55000"));
        service.applyCashDelta(close.sellerAccountId(), new BigDecimal("55000"));

        Portfolio seller = service.portfolio(100L);
        assertThat(seller.positions().get(0).quantity()).isEqualTo(new BigDecimal("0"));
        assertThat(seller.positions().get(0).realizedPnl()).isEqualTo(new BigDecimal("5000"));
        assertThat(seller.cash()).isEqualTo(new BigDecimal("1005000")); // 950k + 55k
    }

    @Test
    void markToMarketRevaluesPositionsWhenPriceChanges() {
        PortfolioService service = new PortfolioService();
        Trade open = new Trade(1L, 1L, 2L, "BTC-USD",
                new BigDecimal("50000"), new BigDecimal("1"), NOW, 100L, 200L, 1L);
        service.applyTrade(open, open.price());

        // mark price moves to 55k: long position should show +5k unrealized.
        service.markToMarket("BTC-USD", new BigDecimal("55000"));

        Portfolio buyer = service.portfolio(100L);
        assertThat(buyer.positions().get(0).unrealizedPnl()).isEqualTo(new BigDecimal("5000"));

        // mark price moves to 45k: long position should show -5k unrealized.
        service.markToMarket("BTC-USD", new BigDecimal("45000"));
        assertThat(service.portfolio(100L).positions().get(0).unrealizedPnl())
                .isEqualTo(new BigDecimal("-5000"));
    }

    @Test
    void markToMarketIsIdempotentAtSamePrice() {
        PortfolioService service = new PortfolioService();
        Trade open = new Trade(1L, 1L, 2L, "BTC-USD",
                new BigDecimal("50000"), new BigDecimal("1"), NOW, 100L, 200L, 1L);
        service.applyTrade(open, open.price());
        service.markToMarket("BTC-USD", new BigDecimal("55000"));

        Position first = service.portfolio(100L).positions().get(0);
        assertThat(first.unrealizedPnl()).isEqualTo(new BigDecimal("5000"));

        // Calling again at the same mark price must not recompute and must not mutate state.
        service.markToMarket("BTC-USD", new BigDecimal("55000"));
        Position second = service.portfolio(100L).positions().get(0);

        assertThat(second.unrealizedPnl()).isEqualTo(new BigDecimal("5000"));
    }

    @Test
    void markToMarketAtSamePriceStillCorrectlyUpdatesNewPosition() {
        PortfolioService service = new PortfolioService();
        Trade open = new Trade(1L, 1L, 2L, "BTC-USD",
                new BigDecimal("50000"), new BigDecimal("1"), NOW, 100L, 200L, 1L);
        service.applyTrade(open, new BigDecimal("55000")); // open at 55k mark

        // markToMarket is skipped because the mark price hasn't changed, but the position
        // was already created with the correct unrealized PnL by applyTrade.
        Portfolio buyer = service.portfolio(100L);
        Position position = buyer.positions().get(0);
        assertThat(position.avgPrice()).isEqualTo(new BigDecimal("50000"));
        assertThat(position.unrealizedPnl()).isEqualTo(new BigDecimal("5000"));
    }
}

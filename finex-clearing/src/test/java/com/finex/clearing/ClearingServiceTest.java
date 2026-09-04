package com.finex.clearing;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.finex.common.domain.Trade;
import com.finex.common.domain.enums.Side;

import static org.assertj.core.api.Assertions.assertThat;

class ClearingServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void buyerTakerPaysFeeAndSellerReceivesNet() {
        ClearingService service = new ClearingService(new FeeSchedule(new BigDecimal("0.001"), BigDecimal.ZERO));
        Trade trade = new Trade(1L, 1L, 2L, "BTC-USD",
                new BigDecimal("50000"), new BigDecimal("1"), NOW, 100L, 200L, 1L);

        ClearingResult result = service.clear(trade, Side.BUY);

        assertThat(result.notional()).isEqualByComparingTo(new BigDecimal("50000"));
        assertThat(result.buyerCashDelta()).isEqualByComparingTo(new BigDecimal("-50050")); // -50000 - 50 fee
        assertThat(result.sellerCashDelta()).isEqualByComparingTo(new BigDecimal("50000"));
        assertThat(result.buyerFee()).isEqualByComparingTo(new BigDecimal("50"));
        assertThat(result.sellerFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.feeAccrued()).isEqualByComparingTo(new BigDecimal("50"));
    }

    @Test
    void sellerTakerPaysFeeAndBuyerPaysNotional() {
        ClearingService service = new ClearingService(new FeeSchedule(new BigDecimal("0.001"), BigDecimal.ZERO));
        Trade trade = new Trade(1L, 1L, 2L, "BTC-USD",
                new BigDecimal("50000"), new BigDecimal("1"), NOW, 100L, 200L, 1L);

        ClearingResult result = service.clear(trade, Side.SELL);

        assertThat(result.buyerCashDelta()).isEqualByComparingTo(new BigDecimal("-50000"));
        assertThat(result.sellerCashDelta()).isEqualByComparingTo(new BigDecimal("49950")); // 50000 - 50 fee
        assertThat(result.sellerFee()).isEqualByComparingTo(new BigDecimal("50"));
        assertThat(result.feeAccrued()).isEqualByComparingTo(new BigDecimal("50"));
    }
}

package com.finex.clearing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.finex.common.domain.Trade;
import com.finex.common.domain.enums.Side;

class FixedPointClearingServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void exactlyMatchesBigDecimalReferenceForRepresentableTrade() {
        Trade trade = trade("49990", "0.01");
        ClearingResult reference = new ClearingService().clear(trade, Side.BUY);
        ClearingResult fixedPoint = new FixedPointClearingService().clear(trade, Side.BUY);

        assertThat(fixedPoint.notional()).isEqualByComparingTo(reference.notional());
        assertThat(fixedPoint.buyerCashDelta()).isEqualByComparingTo(reference.buyerCashDelta());
        assertThat(fixedPoint.sellerCashDelta()).isEqualByComparingTo(reference.sellerCashDelta());
        assertThat(fixedPoint.buyerFee()).isEqualByComparingTo(reference.buyerFee());
        assertThat(fixedPoint.sellerFee()).isEqualByComparingTo(reference.sellerFee());
        assertThat(fixedPoint.feeAccrued()).isEqualByComparingTo(reference.feeAccrued());
    }

    @Test
    void failsRatherThanRoundingUnrepresentableDerivedFee() {
        Trade trade = trade("1.0001", "1");

        assertThatThrownBy(() -> new FixedPointClearingService().clear(trade, Side.BUY))
                .isInstanceOf(ArithmeticException.class)
                .hasMessage("product exceeds fixed-point precision");
    }

    private static Trade trade(String price, String quantity) {
        return new Trade(1, 1, 2, "BTC-USD", new BigDecimal(price), new BigDecimal(quantity),
                NOW, 10, 20, 1);
    }
}

package com.finex.clearing;

import java.math.BigDecimal;

/**
 * Net cash and fee obligations for a single trade.
 *
 * <p>Cash deltas are signed from the account's perspective: a buyer's delta is negative
 * (cash leaves), a seller's delta is positive (cash arrives). Fees are always non-negative
 * absolute amounts deducted from the taker/maker as configured.
 */
public record ClearingResult(
        BigDecimal notional,
        BigDecimal buyerCashDelta,
        BigDecimal sellerCashDelta,
        BigDecimal buyerFee,
        BigDecimal sellerFee,
        BigDecimal feeAccrued) {

    public ClearingResult {
        if (notional == null || notional.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("notional must be positive");
        }
        if (buyerCashDelta == null || sellerCashDelta == null) {
            throw new IllegalArgumentException("cash deltas must not be null");
        }
        if (buyerFee == null || buyerFee.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("buyerFee must not be negative");
        }
        if (sellerFee == null || sellerFee.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("sellerFee must not be negative");
        }
        if (feeAccrued == null || feeAccrued.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("feeAccrued must not be negative");
        }
    }
}

package com.finex.clearing;

import java.math.BigDecimal;

import com.finex.common.domain.Trade;
import com.finex.common.domain.enums.Side;

/**
 * Computes net cash obligations for a trade, including maker/taker fees.
 */
public class ClearingService {

    private final FeeSchedule feeSchedule;

    public ClearingService() {
        this(FeeSchedule.defaults());
    }

    public ClearingService(FeeSchedule feeSchedule) {
        if (feeSchedule == null) {
            throw new IllegalArgumentException("feeSchedule must not be null");
        }
        this.feeSchedule = feeSchedule;
    }

    /**
     * Computes the clearing result for a trade.
     *
     * @param trade    the trade to clear
     * @param takerSide the side (BUY or SELL) that removed liquidity (the aggressor)
     * @return the {@link ClearingResult}
     */
    public ClearingResult clear(Trade trade, Side takerSide) {
        if (trade == null) {
            throw new IllegalArgumentException("trade must not be null");
        }
        if (takerSide == null) {
            throw new IllegalArgumentException("takerSide must not be null");
        }

        BigDecimal notional = trade.price().multiply(trade.quantity());
        BigDecimal takerFee = notional.multiply(feeSchedule.takerRate());
        BigDecimal makerFee = notional.multiply(feeSchedule.makerRate());

        BigDecimal buyerFee = takerSide == Side.BUY ? takerFee : makerFee;
        BigDecimal sellerFee = takerSide == Side.SELL ? takerFee : makerFee;

        BigDecimal buyerCashDelta = notional.add(buyerFee).negate();
        BigDecimal sellerCashDelta = notional.subtract(sellerFee);
        BigDecimal feeAccrued = buyerFee.add(sellerFee);

        return new ClearingResult(notional, buyerCashDelta, sellerCashDelta, buyerFee, sellerFee, feeAccrued);
    }
}

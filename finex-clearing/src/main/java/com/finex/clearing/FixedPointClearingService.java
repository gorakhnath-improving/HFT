package com.finex.clearing;

import java.math.BigDecimal;

import com.finex.common.domain.Trade;
import com.finex.common.domain.enums.Side;
import com.finex.common.numeric.FixedPoint;

public final class FixedPointClearingService extends ClearingService {

    private final long takerRate;
    private final long makerRate;

    public FixedPointClearingService() {
        this(FeeSchedule.defaults());
    }

    public FixedPointClearingService(FeeSchedule feeSchedule) {
        super(feeSchedule);
        this.takerRate = FixedPoint.toRawExact(feeSchedule.takerRate());
        this.makerRate = FixedPoint.toRawExact(feeSchedule.makerRate());
    }

    @Override
    public ClearingResult clear(Trade trade, Side takerSide) {
        if (trade == null) {
            throw new IllegalArgumentException("trade must not be null");
        }
        if (takerSide == null) {
            throw new IllegalArgumentException("takerSide must not be null");
        }

        long price = FixedPoint.toRawExact(trade.price());
        long quantity = FixedPoint.toRawExact(trade.quantity());
        long notional = FixedPoint.multiplyExactRaw(price, quantity);
        long takerFee = FixedPoint.multiplyExactRaw(notional, takerRate);
        long makerFee = FixedPoint.multiplyExactRaw(notional, makerRate);
        long buyerFee = takerSide == Side.BUY ? takerFee : makerFee;
        long sellerFee = takerSide == Side.SELL ? takerFee : makerFee;
        long buyerCashDelta = Math.negateExact(Math.addExact(notional, buyerFee));
        long sellerCashDelta = Math.subtractExact(notional, sellerFee);
        long feeAccrued = Math.addExact(buyerFee, sellerFee);

        return new ClearingResult(
                decimal(notional), decimal(buyerCashDelta), decimal(sellerCashDelta),
                decimal(buyerFee), decimal(sellerFee), decimal(feeAccrued));
    }

    private static BigDecimal decimal(long raw) {
        return FixedPoint.toBigDecimal(raw);
    }
}

package com.finex.risk;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.finex.common.domain.Order;
import com.finex.common.domain.Trade;
import com.finex.common.domain.enums.OrderStatus;
import com.finex.common.domain.enums.OrderType;
import com.finex.common.domain.enums.Side;
import com.finex.common.numeric.FixedPoint;

class FixedPointRiskEngineTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void matchesReferenceAcrossReservationTradeAndCancelLifecycle() {
        RiskConfig config = RiskConfig.defaults();
        RiskEngine referenceEngine = new RiskEngine(config);
        FixedPointRiskEngine fixedEngine = new FixedPointRiskEngine(config);
        AccountRiskState reference = new AccountRiskState(1, decimal("500000"), BigDecimal.ZERO, config);
        FixedPointAccountRiskState fixed = new FixedPointAccountRiskState(1, decimal("500000"), BigDecimal.ZERO);
        Order first = order(1, Side.BUY, "50000", "2");
        Order second = order(2, Side.BUY, "40000", "1");

        assertThat(fixedEngine.validate(first, fixed, NOW, decimal("50000")))
                .isEqualTo(referenceEngine.validate(first, reference, NOW, decimal("50000")));
        assertStateEquals(reference, fixed);

        Trade trade = new Trade(1, 1, 3, "BTC-USD", decimal("49990"), decimal("0.5"),
                NOW.plusMillis(1), 1, 2, 1);
        referenceEngine.onTrade(reference, 1, trade, Side.BUY);
        fixedEngine.onTrade(fixed, 1, trade, Side.BUY);
        assertStateEquals(reference, fixed);

        assertThat(fixedEngine.validate(second, fixed, NOW.plusMillis(2), decimal("49990")))
                .isEqualTo(referenceEngine.validate(second, reference, NOW.plusMillis(2), decimal("49990")));
        referenceEngine.onCancel(reference, 2);
        fixedEngine.onCancel(fixed, 2);
        assertStateEquals(reference, fixed);
    }

    @Test
    void matchesReferenceRiskRejectionBoundaries() {
        RiskConfig config = RiskConfig.defaults();
        RiskEngine referenceEngine = new RiskEngine(config);
        FixedPointRiskEngine fixedEngine = new FixedPointRiskEngine(config);

        for (String price : new String[] {"45000", "45000.0001", "55000", "55000.0001"}) {
            AccountRiskState reference = new AccountRiskState(1, decimal("500000"), BigDecimal.ZERO, config);
            FixedPointAccountRiskState fixed = new FixedPointAccountRiskState(1, decimal("500000"), BigDecimal.ZERO);
            Order order = order(1, Side.BUY, price, "1");
            assertThat(fixedEngine.validate(order, fixed, NOW, decimal("50000")))
                    .isEqualTo(referenceEngine.validate(order, reference, NOW, decimal("50000")));
        }
    }

    private static void assertStateEquals(AccountRiskState reference, FixedPointAccountRiskState fixed) {
        assertThat(FixedPoint.toBigDecimal(fixed.cashRaw())).isEqualByComparingTo(reference.cash());
        assertThat(FixedPoint.toBigDecimal(fixed.positionRaw())).isEqualByComparingTo(reference.position());
        assertThat(FixedPoint.toBigDecimal(fixed.reservedCashRaw())).isEqualByComparingTo(reference.reservedCash());
        assertThat(FixedPoint.toBigDecimal(fixed.reservedPositionRaw())).isEqualByComparingTo(reference.reservedPosition());
    }

    private static Order order(long id, Side side, String price, String quantity) {
        return new Order(id, "cid-" + id, 1, "BTC-USD", side, OrderType.LIMIT,
                decimal(price), decimal(quantity), decimal(quantity), id, NOW, OrderStatus.OPEN);
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}

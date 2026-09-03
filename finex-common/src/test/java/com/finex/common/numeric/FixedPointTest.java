package com.finex.common.numeric;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.util.Random;

import org.junit.jupiter.api.Test;

class FixedPointTest {

    @Test
    void constructsAndConvertsExactly() {
        assertThat(FixedPoint.from(new BigDecimal("123.4567")).raw()).isEqualTo(1_234_567L);
        assertThat(FixedPoint.fromRaw(1_234_567L).toBigDecimal()).isEqualByComparingTo("123.4567");
        assertThat(FixedPoint.from(BigDecimal.ZERO)).isSameAs(FixedPoint.ZERO);
    }

    @Test
    void rejectsUnsupportedPrecisionAndRange() {
        assertThatThrownBy(() -> FixedPoint.from(new BigDecimal("0.00001")))
                .isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> FixedPoint.from(new BigDecimal("922337203685477.5808")))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void addsSubtractsComparesAndNegates() {
        FixedPoint a = FixedPoint.from(new BigDecimal("12.3456"));
        FixedPoint b = FixedPoint.from(new BigDecimal("1.2345"));

        assertThat(a.add(b).toBigDecimal()).isEqualByComparingTo("13.5801");
        assertThat(a.subtract(b).toBigDecimal()).isEqualByComparingTo("11.1111");
        assertThat(a.negate().abs()).isEqualTo(a);
        assertThat(a.min(b)).isEqualTo(b);
        assertThat(a.max(b)).isEqualTo(a);
        assertThat(a.compareTo(a)).isZero();
    }

    @Test
    void detectsAddSubtractAndNegationOverflow() {
        assertThatThrownBy(() -> FixedPoint.MAX_VALUE.add(FixedPoint.fromRaw(1)))
                .isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> FixedPoint.MIN_VALUE.subtract(FixedPoint.fromRaw(1)))
                .isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(FixedPoint.MIN_VALUE::negate).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void multiplicationMatchesBigDecimalHalfUpIncludingBoundaries() {
        assertProduct("1.2345", "2.3456");
        assertProduct("0.0001", "0.5000");
        assertProduct("-0.0001", "0.5000");
        assertProduct("50000", "0.01");
        assertProduct("92233720368", "0.0001");
    }

    @Test
    void divisionMatchesBigDecimalHalfUpIncludingBoundaries() {
        assertQuotient("1", "3");
        assertQuotient("2", "3");
        assertQuotient("-1", "3");
        assertQuotient("1", "-8");
        assertQuotient("92233720368", "2");
        assertThatThrownBy(() -> FixedPoint.from(BigDecimal.ONE).divide(FixedPoint.ZERO))
                .isInstanceOf(ArithmeticException.class)
                .hasMessage("division by zero");
    }

    @Test
    void detectsRoundedMultiplicationAndDivisionOverflow() {
        assertThatThrownBy(() -> FixedPoint.MAX_VALUE.multiply(FixedPoint.from(new BigDecimal("2"))))
                .isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> FixedPoint.MAX_VALUE.divide(FixedPoint.from(new BigDecimal("0.0001"))))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void serializesRawValueDeterministically() {
        FixedPoint value = FixedPoint.from(new BigDecimal("-123.4567"));
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        value.writeTo(buffer);
        buffer.flip();

        assertThat(FixedPoint.readFrom(buffer)).isEqualTo(value);
    }

    @Test
    void randomizedPropertiesAndBigDecimalEquivalence() {
        Random random = new Random(10L);
        for (int i = 0; i < 10_000; i++) {
            FixedPoint a = FixedPoint.fromRaw(random.nextInt(2_000_001) - 1_000_000L);
            FixedPoint b = FixedPoint.fromRaw(random.nextInt(2_000_001) - 1_000_000L);

            assertThat(a.add(FixedPoint.ZERO)).isEqualTo(a);
            assertThat(a.subtract(a)).isEqualTo(FixedPoint.ZERO);
            assertThat(a.add(b)).isEqualTo(b.add(a));
            assertThat(a.compareTo(a)).isZero();
            assertThat(a.multiply(b).toBigDecimal()).isEqualByComparingTo(
                    a.toBigDecimal().multiply(b.toBigDecimal()).setScale(FixedPoint.SCALE, RoundingMode.HALF_UP));
            if (b.raw() != 0) {
                assertThat(a.divide(b).toBigDecimal()).isEqualByComparingTo(
                        a.toBigDecimal().divide(b.toBigDecimal(), FixedPoint.SCALE, RoundingMode.HALF_UP));
            }
        }
    }

    private static void assertProduct(String left, String right) {
        BigDecimal a = new BigDecimal(left);
        BigDecimal b = new BigDecimal(right);
        assertThat(FixedPoint.from(a).multiply(FixedPoint.from(b)).toBigDecimal()).isEqualByComparingTo(
                a.multiply(b).setScale(FixedPoint.SCALE, RoundingMode.HALF_UP));
    }

    private static void assertQuotient(String dividend, String divisor) {
        BigDecimal a = new BigDecimal(dividend);
        BigDecimal b = new BigDecimal(divisor);
        assertThat(FixedPoint.from(a).divide(FixedPoint.from(b)).toBigDecimal()).isEqualByComparingTo(
                a.divide(b, FixedPoint.SCALE, RoundingMode.HALF_UP));
    }
}

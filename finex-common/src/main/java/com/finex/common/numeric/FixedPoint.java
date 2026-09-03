package com.finex.common.numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.ByteBuffer;

public record FixedPoint(long raw) implements Comparable<FixedPoint> {

    public static final int SCALE = 4;
    public static final long FACTOR = 10_000L;
    public static final FixedPoint ZERO = new FixedPoint(0);
    public static final FixedPoint MIN_VALUE = new FixedPoint(Long.MIN_VALUE);
    public static final FixedPoint MAX_VALUE = new FixedPoint(Long.MAX_VALUE);

    public static FixedPoint from(BigDecimal value) {
        return fromRaw(toRawExact(value));
    }

    public static long toRawExact(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        return value.setScale(SCALE, RoundingMode.UNNECESSARY).unscaledValue().longValueExact();
    }

    public static BigDecimal toBigDecimal(long raw) {
        return BigDecimal.valueOf(raw, SCALE);
    }

    public static long addRaw(long left, long right) {
        return Math.addExact(left, right);
    }

    public static long subtractRaw(long left, long right) {
        return Math.subtractExact(left, right);
    }

    public static long multiplyRaw(long left, long right) {
        return scaleProduct(left, right);
    }

    public static long multiplyExactRaw(long left, long right) {
        try {
            long product = Math.multiplyExact(left, right);
            if (product % FACTOR != 0) {
                throw new ArithmeticException("product exceeds fixed-point precision");
            }
            return product / FACTOR;
        } catch (ArithmeticException overflow) {
            BigInteger[] result = BigInteger.valueOf(left).multiply(BigInteger.valueOf(right))
                    .divideAndRemainder(BigInteger.valueOf(FACTOR));
            if (result[1].signum() != 0) {
                throw new ArithmeticException("product exceeds fixed-point precision");
            }
            return result[0].longValueExact();
        }
    }

    public static long divideRaw(long dividend, long divisor) {
        if (divisor == 0) {
            throw new ArithmeticException("division by zero");
        }
        return scaleQuotient(dividend, divisor);
    }

    public static FixedPoint fromRaw(long raw) {
        return raw == 0 ? ZERO : new FixedPoint(raw);
    }

    public FixedPoint add(FixedPoint other) {
        return fromRaw(Math.addExact(raw, other.raw));
    }

    public FixedPoint subtract(FixedPoint other) {
        return fromRaw(Math.subtractExact(raw, other.raw));
    }

    public FixedPoint multiply(FixedPoint other) {
        return fromRaw(scaleProduct(raw, other.raw));
    }

    public FixedPoint divide(FixedPoint other) {
        if (other.raw == 0) {
            throw new ArithmeticException("division by zero");
        }
        return fromRaw(scaleQuotient(raw, other.raw));
    }

    public FixedPoint negate() {
        return fromRaw(Math.negateExact(raw));
    }

    public FixedPoint abs() {
        return raw < 0 ? negate() : this;
    }

    public FixedPoint min(FixedPoint other) {
        return raw <= other.raw ? this : other;
    }

    public FixedPoint max(FixedPoint other) {
        return raw >= other.raw ? this : other;
    }

    public BigDecimal toBigDecimal() {
        return toBigDecimal(raw);
    }

    public void writeTo(ByteBuffer buffer) {
        buffer.putLong(raw);
    }

    public static FixedPoint readFrom(ByteBuffer buffer) {
        return fromRaw(buffer.getLong());
    }

    @Override
    public int compareTo(FixedPoint other) {
        return Long.compare(raw, other.raw);
    }

    private static long scaleProduct(long left, long right) {
        try {
            return divideAndRound(Math.multiplyExact(left, right), FACTOR);
        } catch (ArithmeticException overflow) {
            return bigIntegerResult(BigInteger.valueOf(left).multiply(BigInteger.valueOf(right)), BigInteger.valueOf(FACTOR));
        }
    }

    private static long scaleQuotient(long dividend, long divisor) {
        try {
            return divideAndRound(Math.multiplyExact(dividend, FACTOR), divisor);
        } catch (ArithmeticException overflow) {
            return bigIntegerResult(BigInteger.valueOf(dividend).multiply(BigInteger.valueOf(FACTOR)), BigInteger.valueOf(divisor));
        }
    }

    private static long divideAndRound(long dividend, long divisor) {
        long quotient = dividend / divisor;
        long remainder = dividend % divisor;
        if (remainder == 0) {
            return quotient;
        }
        long threshold = Math.abs(divisor / 2) + Math.abs(divisor % 2);
        if (Math.abs(remainder) >= threshold) {
            return Math.addExact(quotient, Long.signum(dividend) * Long.signum(divisor));
        }
        return quotient;
    }

    private static long bigIntegerResult(BigInteger dividend, BigInteger divisor) {
        BigInteger[] quotientAndRemainder = dividend.divideAndRemainder(divisor);
        BigInteger quotient = quotientAndRemainder[0];
        BigInteger remainder = quotientAndRemainder[1];
        if (remainder.signum() != 0 && remainder.abs().shiftLeft(1).compareTo(divisor.abs()) >= 0) {
            quotient = quotient.add(BigInteger.valueOf(dividend.signum() * divisor.signum()));
        }
        return quotient.longValueExact();
    }
}

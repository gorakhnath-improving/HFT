package com.finex.common.domain;

import java.math.BigDecimal;

/**
 * Cash or asset balance for an account.
 * `available` is the amount free to use; `reserved` is the amount locked by open orders
 * or pending settlement. Total = available + reserved.
 *
 * @param accountId owning account
 * @param asset     asset or currency code, e.g. "USD", "BTC"
 * @param available amount available for use; must be non-negative
 * @param reserved  amount reserved/locked; must be non-negative
 */
public record Balance(long accountId, String asset, BigDecimal available, BigDecimal reserved) {

    public Balance {
        if (asset == null || asset.isBlank()) {
            throw new IllegalArgumentException("asset must not be blank");
        }
        if (available == null || available.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("available must not be negative");
        }
        if (reserved == null || reserved.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("reserved must not be negative");
        }
    }

    /**
     * Returns the total balance (available + reserved).
     */
    public BigDecimal total() {
        return available.add(reserved);
    }
}

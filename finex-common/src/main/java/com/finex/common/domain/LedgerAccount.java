package com.finex.common.domain;

import com.finex.common.domain.enums.LedgerAccountType;

/**
 * A ledger account for double-entry bookkeeping (Phase 11 lead-in).
 * Each ledger account belongs to a trading account and tracks a single line of value.
 *
 * @param id          unique ledger account id
 * @param accountId   owning trading account; -1 for system-level accounts
 * @param asset       asset or currency code ("USD", "BTC"), null for non-asset P&L accounts
 * @param type        ledger account type
 * @param name        human-readable name
 */
public record LedgerAccount(long id, long accountId, String asset, LedgerAccountType type, String name) {

    public LedgerAccount {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
    }
}

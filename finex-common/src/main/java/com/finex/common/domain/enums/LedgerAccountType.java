package com.finex.common.domain.enums;

/**
 * Classification of a ledger account.
 * Kept minimal for the baseline; can expand to full five-account accounting later.
 */
public enum LedgerAccountType {
    CASH,
    ASSET,
    FEE,
    REALIZED_PNL
}

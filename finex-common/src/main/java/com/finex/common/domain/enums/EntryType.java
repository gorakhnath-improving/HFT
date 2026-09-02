package com.finex.common.domain.enums;

/**
 * Type of a ledger entry, used for grouping and audit (Master Plan §21).
 */
public enum EntryType {
    TRADE,
    FEE,
    SETTLEMENT,
    ADJUSTMENT
}

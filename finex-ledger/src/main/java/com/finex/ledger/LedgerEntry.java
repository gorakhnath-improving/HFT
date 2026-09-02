package com.finex.ledger;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable double-entry ledger line. A balanced posting consists of one or more entries
 * whose total DEBIT amounts equal total CREDIT amounts.
 */
public record LedgerEntry(
        long id,
        Instant timestamp,
        String accountCode,
        BigDecimal amount,
        DebitCredit side,
        String currency,
        String narration) {

    public LedgerEntry {
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp must not be null");
        }
        if (accountCode == null || accountCode.isBlank()) {
            throw new IllegalArgumentException("accountCode must not be blank");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (side == null) {
            throw new IllegalArgumentException("side must not be null");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
        if (narration == null || narration.isBlank()) {
            throw new IllegalArgumentException("narration must not be blank");
        }
    }
}

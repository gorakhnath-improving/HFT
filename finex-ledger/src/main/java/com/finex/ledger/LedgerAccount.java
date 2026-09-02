package com.finex.ledger;

/**
 * Immutable ledger account definition.
 */
public record LedgerAccount(String code, AccountType type, String currency, String description) {

    public LedgerAccount {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
    }
}

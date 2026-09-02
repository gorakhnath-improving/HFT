package com.finex.common.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.finex.common.domain.enums.DebitCredit;
import com.finex.common.domain.enums.EntryType;

/**
 * A single entry in the double-entry ledger. Ledger entries are immutable; corrections are
 * represented by new compensating entries (Master Plan §15).
 *
 * @param entryId      unique entry identifier
 * @param ledgerAccountId id of the ledger account this entry posts to
 * @param amount       positive amount of the entry
 * @param side         DEBIT or CREDIT
 * @param type         entry type
 * @param referenceId  related trade/order/settlement id
 * @param groupId      links a balanced set of debit and credit entries
 * @param timestamp    posting timestamp
 */
public record LedgerEntry(
        long entryId,
        long ledgerAccountId,
        BigDecimal amount,
        DebitCredit side,
        EntryType type,
        String referenceId,
        String groupId,
        Instant timestamp) {

    public LedgerEntry {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (side == null) {
            throw new IllegalArgumentException("side must not be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (referenceId == null || referenceId.isBlank()) {
            throw new IllegalArgumentException("referenceId must not be blank");
        }
        if (groupId == null || groupId.isBlank()) {
            throw new IllegalArgumentException("groupId must not be blank");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp must not be null");
        }
    }
}

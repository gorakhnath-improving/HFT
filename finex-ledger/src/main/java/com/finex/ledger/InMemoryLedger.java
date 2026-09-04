package com.finex.ledger;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory, append-only double-entry ledger baseline. It rejects any posting that is not
 * balanced and never mutates or removes existing entries.
 */
public class InMemoryLedger implements Ledger {

    private final List<LedgerEntry> entries = Collections.synchronizedList(new ArrayList<>());
    private final AtomicLong sequence = new AtomicLong(0);

    @Override
    public long post(List<LedgerEntry> entriesToPost) {
        if (entriesToPost == null || entriesToPost.isEmpty()) {
            throw new LedgerException("posting must contain at least one entry");
        }
        if (entriesToPost.size() < 2) {
            throw new LedgerException("posting must contain at least one debit and one credit");
        }

        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        for (LedgerEntry entry : entriesToPost) {
            if (entry.side() == DebitCredit.DEBIT) {
                debits = debits.add(entry.amount());
            } else {
                credits = credits.add(entry.amount());
            }
        }
        if (debits.compareTo(credits) != 0) {
            throw new LedgerException("unbalanced posting: debits=" + debits + " credits=" + credits);
        }

        long firstId = -1;
        for (LedgerEntry entry : entriesToPost) {
            long id = sequence.incrementAndGet();
            if (firstId == -1) {
                firstId = id;
            }
            entries.add(new LedgerEntry(
                    id,
                    entry.timestamp(),
                    entry.accountCode(),
                    entry.amount(),
                    entry.side(),
                    entry.currency(),
                    entry.narration()));
        }
        return firstId;
    }

    @Override
    public List<LedgerEntry> entries() {
        return List.copyOf(entries);
    }

    @Override
    public BigDecimal balance(String accountCode) {
        BigDecimal balance = BigDecimal.ZERO;
        for (LedgerEntry entry : entries) {
            if (entry.accountCode().equals(accountCode)) {
                balance = entry.side() == DebitCredit.DEBIT
                        ? balance.add(entry.amount())
                        : balance.subtract(entry.amount());
            }
        }
        return balance;
    }
}

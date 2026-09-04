package com.finex.ledger;

import java.math.BigDecimal;
import java.util.List;

/**
 * Append-only double-entry ledger.
 */
public interface Ledger {

    /**
     * Posts a balanced set of entries. The sum of DEBIT amounts must equal the sum of
     * CREDIT amounts, otherwise a {@link LedgerException} is thrown and no entries are stored.
     *
     * @param entries the entries forming one balanced posting
     * @return the id of the first entry in the posting
     * @throws LedgerException if the posting is unbalanced or invalid
     */
    long post(List<LedgerEntry> entries);

    /**
     * Returns all ledger entries in insertion order.
     */
    List<LedgerEntry> entries();

    /**
     * Returns the running balance for an account code. Debits increase the balance,
     * credits decrease it (asset-centric convention for this baseline).
     */
    BigDecimal balance(String accountCode);
}

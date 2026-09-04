package com.finex.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryLedgerTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void postBalancedEntriesUpdatesBalances() {
        InMemoryLedger ledger = new InMemoryLedger();

        ledger.post(List.of(
                new LedgerEntry(0, NOW, "CASH.100", new BigDecimal("50000"), DebitCredit.DEBIT, "USD", "deposit"),
                new LedgerEntry(0, NOW, "CASH.200", new BigDecimal("50000"), DebitCredit.CREDIT, "USD", "withdrawal")));

        assertThat(ledger.balance("CASH.100")).isEqualTo(new BigDecimal("50000"));
        assertThat(ledger.balance("CASH.200")).isEqualTo(new BigDecimal("-50000"));
        assertThat(ledger.entries()).hasSize(2);
    }

    @Test
    void postRequiresAtLeastTwoEntries() {
        InMemoryLedger ledger = new InMemoryLedger();

        assertThatThrownBy(() -> ledger.post(List.of(
                new LedgerEntry(0, NOW, "CASH.100", new BigDecimal("100"), DebitCredit.DEBIT, "USD", "single"))))
                .isInstanceOf(LedgerException.class);
    }

    @Test
    void postRejectsUnbalancedEntries() {
        InMemoryLedger ledger = new InMemoryLedger();

        assertThatThrownBy(() -> ledger.post(List.of(
                new LedgerEntry(0, NOW, "CASH.100", new BigDecimal("100"), DebitCredit.DEBIT, "USD", "debit"),
                new LedgerEntry(0, NOW, "CASH.200", new BigDecimal("50"), DebitCredit.CREDIT, "USD", "credit"))))
                .isInstanceOf(LedgerException.class)
                .hasMessageContaining("unbalanced posting");

        assertThat(ledger.entries()).isEmpty();
    }

    @Test
    void entriesAreImmutableCopy() {
        InMemoryLedger ledger = new InMemoryLedger();
        ledger.post(List.of(
                new LedgerEntry(0, NOW, "CASH.100", new BigDecimal("1"), DebitCredit.DEBIT, "USD", "a"),
                new LedgerEntry(0, NOW, "CASH.200", new BigDecimal("1"), DebitCredit.CREDIT, "USD", "b")));

        List<LedgerEntry> entries = ledger.entries();
        assertThatThrownBy(entries::clear).isInstanceOf(UnsupportedOperationException.class);
    }
}

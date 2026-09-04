package com.finex.benchmarks.stress;

import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Compares two {@link EngineSnapshot}s and reports only the *first* divergence found, with
 * enough detail to reproduce and diagnose it, rather than dumping the full state of both
 * snapshots (Master Plan performance-engineering OPT-009 §11).
 */
public final class DifferentialComparator {

    private DifferentialComparator() {
    }

    /**
     * Returns a description of the first mismatch between {@code expected} and
     * {@code actual}, or {@link Optional#empty()} if they are canonically identical.
     */
    public static Optional<String> compare(EngineSnapshot expected, EngineSnapshot actual) {
        Optional<String> orderMismatch = compareOrders(expected, actual);
        if (orderMismatch.isPresent()) {
            return orderMismatch;
        }
        if (!expected.events().equals(actual.events())) {
            return Optional.of("event log mismatch: expected=" + expected.events().size()
                    + " events actual=" + actual.events().size() + " events");
        }
        Optional<String> ledgerMismatch = compareLedgers(expected, actual);
        if (ledgerMismatch.isPresent()) {
            return ledgerMismatch;
        }
        Optional<String> portfolioMismatch = comparePortfolios(expected, actual);
        if (portfolioMismatch.isPresent()) {
            return portfolioMismatch;
        }
        return compareBooks(expected, actual);
    }

    private static Optional<String> compareOrders(EngineSnapshot expected, EngineSnapshot actual) {
        var expectedIds = new TreeSet<>(expected.ordersByOrderId().keySet());
        var actualIds = new TreeSet<>(actual.ordersByOrderId().keySet());
        if (!expectedIds.equals(actualIds)) {
            return Optional.of("order id set mismatch: expected=" + expectedIds + " actual=" + actualIds);
        }
        for (Long orderId : expectedIds) {
            EngineSnapshot.OrderView expectedOrder = expected.ordersByOrderId().get(orderId);
            EngineSnapshot.OrderView actualOrder = actual.ordersByOrderId().get(orderId);
            if (!expectedOrder.equals(actualOrder)) {
                return Optional.of("order mismatch: orderId=" + orderId
                        + "\n  expected=" + expectedOrder
                        + "\n  actual=" + actualOrder);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> compareLedgers(EngineSnapshot expected, EngineSnapshot actual) {
        int expectedSize = expected.ledgerEntries().size();
        int actualSize = actual.ledgerEntries().size();
        int size = Math.min(expectedSize, actualSize);
        for (int i = 0; i < size; i++) {
            EngineSnapshot.LedgerEntryView expectedEntry = expected.ledgerEntries().get(i);
            EngineSnapshot.LedgerEntryView actualEntry = actual.ledgerEntries().get(i);
            if (!expectedEntry.equals(actualEntry)) {
                return Optional.of("ledger entry mismatch at index=" + i
                        + "\n  expected=" + expectedEntry
                        + "\n  actual=" + actualEntry);
            }
        }
        if (expectedSize != actualSize) {
            return Optional.of("ledger entry count mismatch: expected=" + expectedSize + " actual=" + actualSize);
        }
        return Optional.empty();
    }

    private static Optional<String> comparePortfolios(EngineSnapshot expected, EngineSnapshot actual) {
        var expectedAccounts = new TreeSet<>(expected.portfoliosByAccountId().keySet());
        var actualAccounts = new TreeSet<>(actual.portfoliosByAccountId().keySet());
        if (!expectedAccounts.equals(actualAccounts)) {
            return Optional.of("portfolio account set mismatch: expected=" + expectedAccounts
                    + " actual=" + actualAccounts);
        }
        for (Long accountId : expectedAccounts) {
            EngineSnapshot.PortfolioView expectedPortfolio = expected.portfoliosByAccountId().get(accountId);
            EngineSnapshot.PortfolioView actualPortfolio = actual.portfoliosByAccountId().get(accountId);
            if (!expectedPortfolio.equals(actualPortfolio)) {
                return Optional.of("portfolio mismatch: accountId=" + accountId
                        + "\n  expected=" + expectedPortfolio
                        + "\n  actual=" + actualPortfolio);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> compareBooks(EngineSnapshot expected, EngineSnapshot actual) {
        for (Map.Entry<String, EngineSnapshot.BookView> entry : expected.booksBySymbol().entrySet()) {
            String symbol = entry.getKey();
            EngineSnapshot.BookView expectedBook = entry.getValue();
            EngineSnapshot.BookView actualBook = actual.booksBySymbol().get(symbol);
            if (!expectedBook.equals(actualBook)) {
                return Optional.of("order book mismatch: symbol=" + symbol
                        + "\n  expected=" + expectedBook
                        + "\n  actual=" + actualBook);
            }
        }
        return Optional.empty();
    }
}

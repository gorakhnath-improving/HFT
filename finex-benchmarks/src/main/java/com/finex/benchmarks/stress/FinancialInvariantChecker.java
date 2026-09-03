package com.finex.benchmarks.stress;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.finex.api.order.OrderResponse;
import com.finex.api.order.OrderService;
import com.finex.common.domain.enums.OrderStatus;
import com.finex.ledger.LedgerEntry;
import com.finex.ledger.DebitCredit;
import com.finex.portfolio.Portfolio;
import com.finex.portfolio.Position;

/**
 * Checks financial/exchange invariants against the final state of an {@link OrderService}
 * after a stress scenario has run. These invariants are derived directly from FinEx's
 * existing accounting model (see {@code finex-clearing}, {@code finex-ledger},
 * {@code finex-portfolio}) — no new financial rules are introduced here.
 *
 * <p>Every account not explicitly touched by this scenario is ignored: {@code context}
 * carries the exact set of accounts/symbols/orders the scenario created, so a violation can
 * always be attributed to something this scenario did.
 */
public final class FinancialInvariantChecker {

    private static final BigDecimal DEFAULT_INITIAL_CASH = new BigDecimal("1000000");

    private FinancialInvariantChecker() {
    }

    public static List<String> check(OrderService orderService, ExecutionResult context) {
        List<String> violations = new ArrayList<>();
        checkCashConservation(orderService, context, violations);
        checkAssetConservation(orderService, context, violations);
        checkLedgerDoubleEntryBalance(orderService, violations);
        checkOrderQuantityConservation(orderService, context, violations);
        checkAccountIsolation(orderService, context, violations);
        return violations;
    }

    /**
     * Cash lost by accounts (relative to the default starting balance) must equal exactly
     * what accrued to the fee account. No cash may be created or destroyed.
     */
    private static void checkCashConservation(OrderService orderService, ExecutionResult context,
                                                List<String> violations) {
        BigDecimal totalDelta = BigDecimal.ZERO;
        for (Long accountId : context.accountIds()) {
            Portfolio portfolio = orderService.portfolio(accountId);
            totalDelta = totalDelta.add(portfolio.cash().subtract(DEFAULT_INITIAL_CASH));
        }
        BigDecimal feeAccrued = orderService.ledger().balance("FEE.ACCRUAL");
        BigDecimal residual = totalDelta.add(feeAccrued);
        if (residual.compareTo(BigDecimal.ZERO) != 0) {
            violations.add("CASH_CONSERVATION violated: sum(cash - initial) + FEE.ACCRUAL should be 0"
                    + " but was " + residual + " (sum(cash-initial)=" + totalDelta
                    + ", FEE.ACCRUAL=" + feeAccrued + ")");
        }
    }

    /**
     * For every symbol touched, the sum of every account's position quantity must be zero:
     * FinEx does not issue or destroy the underlying asset, only transfers it between the
     * buyer and seller of each trade.
     */
    private static void checkAssetConservation(OrderService orderService, ExecutionResult context,
                                                 List<String> violations) {
        for (String symbol : context.symbols()) {
            BigDecimal totalQuantity = BigDecimal.ZERO;
            for (Long accountId : context.accountIds()) {
                Portfolio portfolio = orderService.portfolio(accountId);
                for (Position position : portfolio.positions()) {
                    if (position.symbol().equals(symbol)) {
                        totalQuantity = totalQuantity.add(position.quantity());
                    }
                }
            }
            if (totalQuantity.compareTo(BigDecimal.ZERO) != 0) {
                violations.add("ASSET_CONSERVATION violated: symbol=" + symbol
                        + " sum(position.quantity) should be 0 but was " + totalQuantity);
            }
        }
    }

    /**
     * Every ledger posting is already required to balance at write time
     * ({@code InMemoryLedger.post}); this re-checks it globally as a defense-in-depth
     * sanity check on the full entry list captured by the snapshot/executor.
     */
    private static void checkLedgerDoubleEntryBalance(OrderService orderService, List<String> violations) {
        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        for (LedgerEntry entry : orderService.ledger().entries()) {
            if (entry.side() == DebitCredit.DEBIT) {
                debits = debits.add(entry.amount());
            } else {
                credits = credits.add(entry.amount());
            }
        }
        if (debits.compareTo(credits) != 0) {
            violations.add("LEDGER_DOUBLE_ENTRY_BALANCE violated: total debits=" + debits
                    + " total credits=" + credits);
        }
    }

    /**
     * Every order's remaining quantity must be between zero and its original quantity, and
     * its status must agree with whether it is fully filled.
     */
    private static void checkOrderQuantityConservation(OrderService orderService, ExecutionResult context,
                                                         List<String> violations) {
        for (Long orderId : context.orderIds()) {
            OrderResponse order = orderService.getOrder(orderId).orElse(null);
            if (order == null) {
                violations.add("ORDER_QUANTITY_CONSERVATION violated: orderId=" + orderId + " has no state");
                continue;
            }
            if (order.remainingQuantity().compareTo(BigDecimal.ZERO) < 0
                    || order.remainingQuantity().compareTo(order.quantity()) > 0) {
                violations.add("ORDER_QUANTITY_CONSERVATION violated: orderId=" + orderId
                        + " remaining=" + order.remainingQuantity() + " quantity=" + order.quantity());
            }
            boolean remainingIsZero = order.remainingQuantity().compareTo(BigDecimal.ZERO) == 0;
            if (order.status() == OrderStatus.FILLED && !remainingIsZero) {
                violations.add("ORDER_QUANTITY_CONSERVATION violated: orderId=" + orderId
                        + " is FILLED but remaining=" + order.remainingQuantity());
            }
            if ((order.status() == OrderStatus.OPEN || order.status() == OrderStatus.PARTIALLY_FILLED)
                    && remainingIsZero) {
                violations.add("ORDER_QUANTITY_CONSERVATION violated: orderId=" + orderId
                        + " is " + order.status() + " but remaining=0");
            }
        }
    }

    /**
     * Every order must still belong to the account that originally submitted it: one
     * account's commands must never mutate another account's order.
     */
    private static void checkAccountIsolation(OrderService orderService, ExecutionResult context,
                                                List<String> violations) {
        for (Map.Entry<Integer, Long> entry : context.logicalIndexToOrderId().entrySet()) {
            long orderId = entry.getValue();
            long expectedAccountId = context.logicalIndexToAccountId().get(entry.getKey());
            OrderResponse order = orderService.getOrder(orderId).orElse(null);
            if (order != null && order.accountId() != expectedAccountId) {
                violations.add("ACCOUNT_ISOLATION violated: orderId=" + orderId
                        + " expectedAccountId=" + expectedAccountId + " actualAccountId=" + order.accountId());
            }
        }
    }
}

package com.finex.benchmarks.stress;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.finex.api.order.OrderService;

/**
 * Bookkeeping produced by {@link CommandExecutor#execute} alongside the {@link OrderService}
 * itself: the mapping from a command's {@code logicalIndex} to the exchange-assigned order id
 * (only known after execution), the account id each submit was made under (for the account
 * isolation invariant), and the set of accounts/symbols touched (so invariant checks and
 * snapshots only look at state this scenario actually created).
 */
public final class ExecutionResult {

    private final OrderService orderService;
    private final Map<Integer, Long> logicalIndexToOrderId = new HashMap<>();
    private final Map<Integer, Long> logicalIndexToAccountId = new HashMap<>();
    private final Set<Long> accountIds = new TreeSet<>();
    private final Set<String> symbols = new TreeSet<>();
    private int rejectedCount;
    private int cancelAttempts;
    private int cancelSuccesses;

    ExecutionResult(OrderService orderService) {
        this.orderService = orderService;
    }

    public OrderService orderService() {
        return orderService;
    }

    void recordSubmit(int logicalIndex, long orderId, long accountId, String symbol) {
        logicalIndexToOrderId.put(logicalIndex, orderId);
        logicalIndexToAccountId.put(logicalIndex, accountId);
        accountIds.add(accountId);
        symbols.add(symbol);
    }

    void recordRejected() {
        rejectedCount++;
    }

    void recordCancelAttempt(boolean succeeded) {
        cancelAttempts++;
        if (succeeded) {
            cancelSuccesses++;
        }
    }

    public Map<Integer, Long> logicalIndexToOrderId() {
        return Map.copyOf(logicalIndexToOrderId);
    }

    public Map<Integer, Long> logicalIndexToAccountId() {
        return Map.copyOf(logicalIndexToAccountId);
    }

    public Set<Long> accountIds() {
        return Set.copyOf(accountIds);
    }

    public Set<String> symbols() {
        return Set.copyOf(symbols);
    }

    public int rejectedCount() {
        return rejectedCount;
    }

    public int cancelAttempts() {
        return cancelAttempts;
    }

    public int cancelSuccesses() {
        return cancelSuccesses;
    }

    public Set<Long> orderIds() {
        return new HashSet<>(logicalIndexToOrderId.values());
    }
}

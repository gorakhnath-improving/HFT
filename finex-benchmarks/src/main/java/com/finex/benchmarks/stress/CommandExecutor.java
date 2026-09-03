package com.finex.benchmarks.stress;

import java.util.List;

import com.finex.api.order.OrderRejectedException;
import com.finex.api.order.OrderResponse;
import com.finex.api.order.OrderService;
import com.finex.matching.MatchResult;

/**
 * Executes a previously generated, deterministic command sequence against a single
 * {@link OrderService} instance, in order, recording the exchange-assigned order ids and
 * originating accounts needed for later canonical-state comparison and invariant checking.
 *
 * <p>Risk rejections are an expected, valid outcome (the risk engine may reject an order
 * deterministically, e.g. on rate limits) and are recorded rather than treated as a harness
 * failure; both engines given the same input must reject in the same way, which is exactly
 * what the differential comparison verifies.
 */
public final class CommandExecutor {

    private CommandExecutor() {
    }

    public static ExecutionResult execute(OrderService orderService, List<GeneratedCommand> commands) {
        ExecutionResult result = new ExecutionResult(orderService);
        for (GeneratedCommand command : commands) {
            switch (command) {
                case GeneratedCommand.Submit submit -> executeSubmit(orderService, result, submit);
                case GeneratedCommand.Cancel cancel -> executeCancel(orderService, result, cancel);
            }
        }
        return result;
    }

    private static void executeSubmit(OrderService orderService, ExecutionResult result,
                                       GeneratedCommand.Submit submit) {
        try {
            MatchResult matchResult = orderService.submitOrder(submit.request(), submit.timestamp());
            result.recordSubmit(
                    submit.logicalIndex(),
                    matchResult.order().orderId(),
                    submit.request().accountId(),
                    submit.request().symbol());
        } catch (OrderRejectedException e) {
            result.recordRejected();
        }
    }

    private static void executeCancel(OrderService orderService, ExecutionResult result,
                                       GeneratedCommand.Cancel cancel) {
        Long orderId = result.logicalIndexToOrderId().get(cancel.targetLogicalIndex());
        if (orderId == null) {
            // The target submit was rejected at generation time by risk checks and never
            // received an order id; there is nothing to cancel. This is expected.
            result.recordCancelAttempt(false);
            return;
        }
        boolean cancelled = orderService.cancelOrder(orderId, cancel.timestamp());
        result.recordCancelAttempt(cancelled);
    }

    /** Convenience accessor kept for readability at call sites; not otherwise used. */
    public static OrderResponse requireOrder(OrderService orderService, long orderId) {
        return orderService.getOrder(orderId)
                .orElseThrow(() -> new IllegalStateException("expected order " + orderId + " to exist"));
    }
}

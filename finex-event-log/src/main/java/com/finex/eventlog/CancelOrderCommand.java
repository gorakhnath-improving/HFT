package com.finex.eventlog;

/**
 * Immutable command to cancel an existing order. Used as the event-log payload for replay.
 */
public record CancelOrderCommand(long accountId, long orderId) {

    public CancelOrderCommand {
        if (accountId <= 0) {
            throw new IllegalArgumentException("accountId must be positive");
        }
        if (orderId <= 0) {
            throw new IllegalArgumentException("orderId must be positive");
        }
    }
}

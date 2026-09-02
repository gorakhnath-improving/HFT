package com.finex.eventlog;

import java.time.Instant;

/**
 * Handler used by {@link ReplayEngine} to apply commands to a concrete service.
 */
public interface CommandHandler {

    void submitOrder(SubmitOrderCommand command, Instant timestamp);

    void cancelOrder(CancelOrderCommand command, Instant timestamp);
}

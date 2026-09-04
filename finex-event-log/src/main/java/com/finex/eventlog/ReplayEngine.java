package com.finex.eventlog;

import java.util.List;

/**
 * Replays all events from an {@link EventStore} onto a {@link CommandHandler}.
 */
public final class ReplayEngine {

    private ReplayEngine() {
    }

    public static void replay(EventStore store, CommandHandler handler) {
        if (store == null) {
            throw new IllegalArgumentException("store must not be null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler must not be null");
        }
        List<Event> events = store.readAll();
        for (Event event : events) {
            switch (event.type()) {
                case Event.SUBMIT_ORDER -> handler.submitOrder(CommandSerializer.toSubmitOrderCommand(event), event.timestamp());
                case Event.CANCEL_ORDER -> handler.cancelOrder(CommandSerializer.toCancelOrderCommand(event), event.timestamp());
                default -> throw new IllegalArgumentException("unknown event type: " + event.type());
            }
        }
    }
}

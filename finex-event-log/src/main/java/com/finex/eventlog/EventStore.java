package com.finex.eventlog;

import java.time.Instant;
import java.util.List;

/**
 * Append-only event store. Events are immutable and ordered by insertion sequence.
 */
public interface EventStore {

    /**
     * Appends an event and returns its assigned id.
     */
    long append(Event event);

    /**
     * Appends an event from its raw payload and returns its assigned id.
     */
    long append(Instant timestamp, String type, byte[] payload);

    /**
     * Returns all events in insertion order.
     */
    List<Event> readAll();

    /**
     * Returns the number of stored events.
     */
    long size();
}

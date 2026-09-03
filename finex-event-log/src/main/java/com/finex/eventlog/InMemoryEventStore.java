package com.finex.eventlog;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory append-only event store baseline. This is not durable and is intended for
 * correctness testing and single-node baselines.
 */
public class InMemoryEventStore implements EventStore {

    private final List<Event> events = Collections.synchronizedList(new ArrayList<>());
    private final AtomicLong sequence = new AtomicLong(0);

    @Override
    public long append(Event event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        // Clone to keep the Event-store boundary safe when callers reuse or mutate arrays.
        return append(event.timestamp(), event.type(), event.payload().clone());
    }

    @Override
    public long append(Instant timestamp, String type, byte[] payload) {
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp must not be null");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
        long id = sequence.incrementAndGet();
        events.add(new Event(id, timestamp, type, payload));
        return id;
    }

    @Override
    public List<Event> readAll() {
        return List.copyOf(events);
    }

    @Override
    public long size() {
        return events.size();
    }
}

package com.finex.eventlog;

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
        long id = sequence.incrementAndGet();
        Event stored = new Event(id, event.timestamp(), event.type(), event.payload());
        events.add(stored);
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

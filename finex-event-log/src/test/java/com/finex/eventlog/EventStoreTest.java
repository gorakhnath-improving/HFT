package com.finex.eventlog;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventStoreTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void appendGeneratesSequentialIds() {
        InMemoryEventStore store = new InMemoryEventStore();

        long id1 = store.append(new Event(0L, NOW, Event.SUBMIT_ORDER, new byte[]{1}));
        long id2 = store.append(new Event(0L, NOW, Event.CANCEL_ORDER, new byte[]{2}));

        assertThat(id1).isEqualTo(1L);
        assertThat(id2).isEqualTo(2L);
        assertThat(store.size()).isEqualTo(2L);
        assertThat(store.readAll()).hasSize(2);
    }

    @Test
    void eventsAreImmutable() {
        InMemoryEventStore store = new InMemoryEventStore();
        byte[] payload = {1, 2, 3};

        store.append(new Event(0L, NOW, Event.SUBMIT_ORDER, payload.clone()));
        payload[0] = 9;

        Event stored = store.readAll().get(0);
        assertThat(stored.payload()).containsExactly(1, 2, 3);
    }
}

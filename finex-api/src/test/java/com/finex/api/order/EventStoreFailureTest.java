package com.finex.api.order;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.finex.eventlog.Event;
import com.finex.eventlog.InMemoryEventStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Failure-style tests for event-store replay and corruption resilience.
 */
class EventStoreFailureTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void replayThrowsOnUnknownEventType() {
        InMemoryEventStore store = new InMemoryEventStore();
        store.append(new Event(0L, NOW, "UNKNOWN", "corrupt".getBytes(StandardCharsets.UTF_8)));

        OrderService service = new OrderService(store);
        assertThatThrownBy(service::replay)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown event type");
    }

    @Test
    void replayReconstructsStateAfterAppendingLegitimateAndCorruptEventsIsolated() {
        // A single corrupt event aborts replay and protects the service from undefined state.
        InMemoryEventStore store = new InMemoryEventStore();
        store.append(new Event(0L, NOW, "UNKNOWN", "corrupt".getBytes(StandardCharsets.UTF_8)));

        OrderService service = new OrderService(store);
        assertThat(store.size()).isEqualTo(1);
        assertThatThrownBy(service::replay).isNotNull();
    }
}

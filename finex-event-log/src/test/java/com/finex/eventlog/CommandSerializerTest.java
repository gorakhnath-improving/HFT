package com.finex.eventlog;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.finex.common.domain.enums.OrderType;
import com.finex.common.domain.enums.Side;

import static org.assertj.core.api.Assertions.assertThat;

class CommandSerializerTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void roundTripSubmitOrderCommand() {
        SubmitOrderCommand original = new SubmitOrderCommand(
                100L, "cid-1", "BTC-USD", Side.BUY, OrderType.LIMIT,
                new BigDecimal("50000"), new BigDecimal("2"));

        Event event = CommandSerializer.toEvent(original, NOW, 7L);
        assertThat(event.type()).isEqualTo(Event.SUBMIT_ORDER);
        assertThat(event.id()).isEqualTo(7L);

        SubmitOrderCommand decoded = CommandSerializer.toSubmitOrderCommand(event);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void roundTripCancelOrderCommand() {
        CancelOrderCommand original = new CancelOrderCommand(100L, 42L);

        Event event = CommandSerializer.toEvent(original, NOW, 3L);
        assertThat(event.type()).isEqualTo(Event.CANCEL_ORDER);

        CancelOrderCommand decoded = CommandSerializer.toCancelOrderCommand(event);
        assertThat(decoded).isEqualTo(original);
    }
}

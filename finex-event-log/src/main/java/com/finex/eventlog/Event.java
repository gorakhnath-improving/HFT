package com.finex.eventlog;

import java.time.Instant;

/**
 * A single entry in the append-only event log.
 */
public record Event(long id, Instant timestamp, String type, byte[] payload) {

    public static final String SUBMIT_ORDER = "SUBMIT_ORDER";
    public static final String CANCEL_ORDER = "CANCEL_ORDER";

    public Event {
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp must not be null");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
        payload = payload.clone();
    }

    public byte[] payload() {
        return payload.clone();
    }
}

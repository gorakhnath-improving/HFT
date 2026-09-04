package com.finex.benchmarks.stress;

import java.time.Instant;

import com.finex.api.order.OrderRequest;

/**
 * A single deterministically generated command in a stress scenario. {@code logicalIndex}
 * is the position of this command in the generated sequence and is stable across any number
 * of re-executions of the same (seed, profile, commandCount) scenario; it is how a
 * {@link Cancel} refers back to an earlier {@link Submit} without needing to know the
 * exchange-assigned order id in advance (that id is only known once a command is actually
 * executed against an engine, and is assigned identically by any engine instance fed the
 * exact same command sequence).
 */
public sealed interface GeneratedCommand {

    int logicalIndex();

    Instant timestamp();

    record Submit(int logicalIndex, OrderRequest request, Instant timestamp) implements GeneratedCommand {
    }

    record Cancel(int logicalIndex, int targetLogicalIndex, Instant timestamp) implements GeneratedCommand {
    }
}

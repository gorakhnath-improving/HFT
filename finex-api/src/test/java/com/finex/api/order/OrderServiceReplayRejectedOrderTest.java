package com.finex.api.order;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.finex.common.domain.enums.OrderType;
import com.finex.common.domain.enums.Side;
import com.finex.eventlog.EventStore;
import com.finex.eventlog.ReplayEngine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for a replay-truncation bug found by the OPT-009 randomized differential
 * stress harness ({@code finex-benchmarks} {@code com.finex.benchmarks.stress} package).
 *
 * <p>{@link OrderService#submitOrder(OrderRequest, Instant)} appends the {@code SUBMIT_ORDER}
 * event to the event store <em>before</em> running the risk check, so a rejected order is
 * still recorded in the log. Before the fix, replaying that log called
 * {@link OrderService#submitOrder(com.finex.eventlog.SubmitOrderCommand, Instant)} (the
 * {@code CommandHandler} override used by {@link ReplayEngine}), which let the resulting
 * {@code OrderRejectedException} propagate out of {@link ReplayEngine#replay}. That aborted
 * the replay loop entirely, silently dropping every event after the first rejected order in
 * the log — a correctness bug in the durability/audit path that the small, hand-written
 * {@code OrderServiceReplayTest} scenario was too short to ever trigger a rejection and
 * therefore never exercised.
 */
class OrderServiceReplayRejectedOrderTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void replaySurvivesARejectedOrderInTheMiddleOfTheLog() {
        OrderService original = new OrderService();

        // Exhaust the default 10-orders-per-second rate limit for account 100 within the
        // same one-second window, so order #11 is deterministically rejected by the risk
        // engine but still appended to the event log (see class javadoc).
        for (int i = 0; i < 10; i++) {
            original.submitOrder(new OrderRequest(
                    "cid-a" + i, "BTC-USD", Side.BUY, OrderType.LIMIT,
                    new BigDecimal("100"), BigDecimal.ONE, 100L), T0.plusMillis(i));
        }
        Instant rejectedAt = T0.plusMillis(50);
        assertThatSubmitIsRejected(original, rejectedAt);

        // A later order, submitted a full second afterward so the rate-limit window has
        // rolled over, must still be captured in the log after the rejected one.
        Instant laterAt = T0.plusSeconds(2);
        original.submitOrder(new OrderRequest(
                "cid-later", "BTC-USD", Side.BUY, OrderType.LIMIT,
                new BigDecimal("101"), BigDecimal.TWO, 100L), laterAt);

        EventStore store = original.eventStore();
        // Before the fix: this line throws OrderRejectedException and the assertions below
        // are never reached, because ReplayEngine aborts on the rejected order's event.
        OrderService replayed = new OrderService(store);
        ReplayEngine.replay(store, replayed);

        assertThat(replayed.eventStore().size()).isEqualTo(original.eventStore().size());
        assertThat(replayed.getOrderBook("BTC-USD")).isEqualTo(original.getOrderBook("BTC-USD"));
        assertThat(replayed.portfolio(100L)).isEqualTo(original.portfolio(100L));
    }

    private static void assertThatSubmitIsRejected(OrderService original, Instant at) {
        try {
            original.submitOrder(new OrderRequest(
                    "cid-rejected", "BTC-USD", Side.BUY, OrderType.LIMIT,
                    new BigDecimal("100"), BigDecimal.ONE, 100L), at);
            throw new AssertionError("expected order #11 within the same second to be rate-limited");
        } catch (OrderRejectedException expected) {
            // expected: order #11 within the same one-second window exceeds
            // RiskConfig.defaults().maxOrdersPerSecond() == 10.
        }
    }
}

package com.finex.benchmarks.stress;

import java.util.List;
import java.util.Optional;

import com.finex.api.order.OrderService;
import com.finex.eventlog.ReplayEngine;

/**
 * OPT-009 correctness oracle: given a deterministic {@code (seed, profile, commandCount)}
 * scenario, this harness answers three questions on the current single implementation,
 * which together are the safety net the plan requires before attempting risky numeric
 * (OPT-010) or concurrency (OPT-011) changes:
 *
 * <ol>
 *   <li><b>Determinism:</b> do two independently constructed engines, fed the exact same
 *       generated command sequence, end up in exactly the same canonical state?</li>
 *   <li><b>Replay equivalence:</b> does replaying the resulting event log into a fresh
 *       engine reproduce exactly the same canonical state as the original execution?</li>
 *   <li><b>Financial invariants:</b> does the resulting state satisfy cash/asset
 *       conservation, ledger balance, order-quantity conservation, and account
 *       isolation?</li>
 * </ol>
 *
 * <p>There is currently only one FinEx engine implementation, so "reference vs optimized"
 * in the OPT-009 specification is realized here as "engine A vs an independently
 * constructed engine B" (question 1) and "direct execution vs event-log replay"
 * (question 2). Once a second implementation (e.g. a fixed-point engine for OPT-010)
 * exists, {@link #run} can be pointed at it directly: it only depends on the public
 * {@link OrderService} surface.
 */
public final class StressHarness {

    private StressHarness() {
    }

    public static StressHarnessResult run(long seed, WorkloadProfile profile, int commandCount) {
        List<GeneratedCommand> commands = CommandGenerator.generate(seed, profile, commandCount);
        long start = System.nanoTime();

        OrderService engineA = new OrderService();
        ExecutionResult contextA = CommandExecutor.execute(engineA, commands);
        EngineSnapshot snapshotA = EngineSnapshot.capture(engineA, contextA);

        OrderService engineB = new OrderService();
        ExecutionResult contextB = CommandExecutor.execute(engineB, commands);
        EngineSnapshot snapshotB = EngineSnapshot.capture(engineB, contextB);

        Optional<String> determinismMismatch = DifferentialComparator.compare(snapshotA, snapshotB);

        OrderService replayedEngine = new OrderService(engineA.eventStore());
        ReplayEngine.replay(engineA.eventStore(), replayedEngine);
        // The replayed engine never called submitOrder(OrderRequest, Instant) itself, so it
        // has no ExecutionResult of its own; it was driven by the exact same commands (via
        // the event log), so contextA's logical-index/order-id/account bookkeeping is still
        // valid for capturing its snapshot.
        EngineSnapshot snapshotReplayed = EngineSnapshot.capture(replayedEngine, contextA);
        Optional<String> replayMismatch = DifferentialComparator.compare(snapshotA, snapshotReplayed);

        List<String> invariantViolations = FinancialInvariantChecker.check(engineA, contextA);

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        return new StressHarnessResult(
                seed, profile, commandCount, elapsedMs,
                determinismMismatch, replayMismatch, invariantViolations,
                contextA.rejectedCount(), contextA.cancelAttempts(), contextA.cancelSuccesses());
    }
}

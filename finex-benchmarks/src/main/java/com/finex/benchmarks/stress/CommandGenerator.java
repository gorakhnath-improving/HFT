package com.finex.benchmarks.stress;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.finex.api.order.OrderRequest;
import com.finex.common.domain.enums.OrderType;
import com.finex.common.domain.enums.Side;

/**
 * Deterministic randomized command generator for the OPT-009 differential/invariant stress
 * harness.
 *
 * <p>Given the same {@code seed}, {@code profile}, and {@code commandCount}, {@link #generate}
 * always returns byte-for-byte the same sequence of commands. This is the property the
 * whole harness depends on: the exact same generated sequence is executed against
 * independent engine instances (and, separately, replayed from the resulting event log), and
 * any divergence is a real bug, not generator noise.
 *
 * <p>Only {@code java.util.Random(seed)} is used, and only through this class, so a failure
 * can always be reproduced by re-running {@code CommandGenerator.generate(seed, profile, n)}.
 */
public final class CommandGenerator {

    private static final BigDecimal SYMBOL_BASE_PRICE = new BigDecimal("1000");
    private static final BigDecimal SYMBOL_PRICE_STEP = new BigDecimal("50");
    private static final BigDecimal TICK = BigDecimal.ONE;

    private CommandGenerator() {
    }

    /**
     * Generates {@code commandCount} deterministic commands for {@code profile} using
     * {@code seed}.
     */
    public static List<GeneratedCommand> generate(long seed, WorkloadProfile profile, int commandCount) {
        if (commandCount <= 0) {
            throw new IllegalArgumentException("commandCount must be positive");
        }
        Random random = new Random(seed);
        List<GeneratedCommand> commands = new ArrayList<>(commandCount);
        List<Integer> openSubmitIndices = new ArrayList<>();
        Instant timestamp = Instant.parse("2026-01-01T00:00:00Z");

        for (int i = 0; i < commandCount; i++) {
            timestamp = timestamp.plusNanos(1_000_000); // 1 ms between commands, deterministic
            boolean canCancel = !openSubmitIndices.isEmpty() && i > 0;
            boolean generateCancel = canCancel && random.nextDouble() < profile.cancelRatio();

            if (generateCancel) {
                int targetPick = random.nextInt(openSubmitIndices.size());
                int targetLogicalIndex = openSubmitIndices.get(targetPick);
                commands.add(new GeneratedCommand.Cancel(i, targetLogicalIndex, timestamp));
                // The target may already be filled/cancelled by the time this command
                // executes; we still remove it from the open pool so we do not generate an
                // unbounded number of cancels against the exact same order.
                openSubmitIndices.remove(targetPick);
            } else {
                OrderRequest request = generateSubmit(random, profile, i);
                commands.add(new GeneratedCommand.Submit(i, request, timestamp));
                openSubmitIndices.add(i);
            }
        }
        return List.copyOf(commands);
    }

    private static OrderRequest generateSubmit(Random random, WorkloadProfile profile, int logicalIndex) {
        int symbolIndex = random.nextInt(profile.symbolCount());
        String symbol = "SYM-" + symbolIndex;
        long accountId = 1 + random.nextInt(profile.accountCount());
        Side side = random.nextBoolean() ? Side.BUY : Side.SELL;

        BigDecimal mid = SYMBOL_BASE_PRICE.add(SYMBOL_PRICE_STEP.multiply(BigDecimal.valueOf(symbolIndex)));
        // jitterTicks in [-10, 10], then biased by the profile's crossingBias so that BUY
        // orders land above (crossing) or below (resting) the mid price on average.
        int jitterTicks = random.nextInt(21) - 10;
        int biasTicks = (int) Math.round(profile.crossingBias() * 8);
        int signedBiasTicks = side == Side.BUY ? biasTicks : -biasTicks;
        BigDecimal price = mid.add(TICK.multiply(BigDecimal.valueOf(jitterTicks + signedBiasTicks)));
        if (price.compareTo(TICK) < 0) {
            price = TICK;
        }

        BigDecimal quantity = BigDecimal.valueOf(1 + random.nextInt(10));
        String clientOrderId = "gen-" + logicalIndex;

        return new OrderRequest(clientOrderId, symbol, side, OrderType.LIMIT, price, quantity, accountId);
    }
}

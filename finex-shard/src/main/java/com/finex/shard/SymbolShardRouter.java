package com.finex.shard;

/**
 * Routes symbols to independent matching-engine shards.
 */
public final class SymbolShardRouter {

    private SymbolShardRouter() {
    }

    /**
     * Returns the shard id for a symbol using a simple non-negative modulo of the symbol's
     * hash code. The returned value is always in the range {@code [0, shardCount)}.
     */
    public static int shardFor(String symbol, int shardCount) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        if (shardCount <= 0) {
            throw new IllegalArgumentException("shardCount must be positive");
        }
        return Math.floorMod(symbol.hashCode(), shardCount);
    }
}

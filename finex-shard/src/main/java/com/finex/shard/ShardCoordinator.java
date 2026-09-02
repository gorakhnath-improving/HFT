package com.finex.shard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages a fixed number of {@link EngineShard} instances and routes symbols to shards.
 */
public class ShardCoordinator {

    private final int shardCount;
    private final List<EngineShard> shards;

    public ShardCoordinator(int shardCount) {
        if (shardCount <= 0) {
            throw new IllegalArgumentException("shardCount must be positive");
        }
        this.shardCount = shardCount;
        List<EngineShard> list = new ArrayList<>(shardCount);
        for (int i = 0; i < shardCount; i++) {
            list.add(new EngineShard(i));
        }
        this.shards = Collections.unmodifiableList(list);
    }

    public int shardCount() {
        return shardCount;
    }

    public EngineShard shardFor(String symbol) {
        return shards.get(SymbolShardRouter.shardFor(symbol, shardCount));
    }

    public List<EngineShard> shards() {
        return shards;
    }
}

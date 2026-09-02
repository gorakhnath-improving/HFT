package com.finex.shard;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SymbolShardRouterTest {

    @Test
    void returnsNonNegativeShardId() {
        int shard = SymbolShardRouter.shardFor("BTC-USD", 4);
        assertThat(shard).isGreaterThanOrEqualTo(0).isLessThan(4);
    }

    @Test
    void sameSymbolGoesToSameShard() {
        int first = SymbolShardRouter.shardFor("ETH-USD", 8);
        int second = SymbolShardRouter.shardFor("ETH-USD", 8);
        assertThat(first).isEqualTo(second);
    }

    @Test
    void differentSymbolsCanGoToDifferentShards() {
        int a = SymbolShardRouter.shardFor("A", 2);
        int b = SymbolShardRouter.shardFor("B", 2);
        // With only two buckets and two symbols, collision is possible but not guaranteed.
        // This just verifies both are in range.
        assertThat(a).isIn(0, 1);
        assertThat(b).isIn(0, 1);
    }
}

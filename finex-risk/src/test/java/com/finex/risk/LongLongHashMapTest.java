package com.finex.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

class LongLongHashMapTest {

    @Test
    void insertsReplacesRemovesAndUsesDefault() {
        LongLongHashMap map = new LongLongHashMap();

        assertThat(map.put(1, 10)).isZero();
        assertThat(map.previousPresent()).isFalse();
        assertThat(map.put(1, 20)).isEqualTo(10);
        assertThat(map.previousPresent()).isTrue();
        assertThat(map.getOrDefault(1, -1)).isEqualTo(20);
        assertThat(map.put(1, 30, 40, 50)).isEqualTo(20);
        assertThat(map.previousSecondaryValue()).isZero();
        assertThat(map.previousTertiaryValue()).isZero();
        assertThat(map.get(1)).isEqualTo(30);
        assertThat(map.previousSecondaryValue()).isEqualTo(40);
        assertThat(map.previousTertiaryValue()).isEqualTo(50);
        assertThat(map.remove(1)).isEqualTo(30);
        assertThat(map.previousSecondaryValue()).isEqualTo(40);
        assertThat(map.previousTertiaryValue()).isEqualTo(50);
        assertThat(map.previousPresent()).isTrue();
        assertThat(map.remove(1)).isZero();
        assertThat(map.previousPresent()).isFalse();
        assertThat(map.getOrDefault(1, -1)).isEqualTo(-1);
    }

    @Test
    void survivesGrowthAndTombstoneReuse() {
        LongLongHashMap map = new LongLongHashMap();
        for (long key = 1; key <= 10_000; key++) {
            map.put(key, key * 3);
        }
        for (long key = 1; key <= 10_000; key += 2) {
            assertThat(map.remove(key)).isEqualTo(key * 3);
        }
        for (long key = 10_001; key <= 20_000; key++) {
            map.put(key, key * 3);
        }
        for (long key = 2; key <= 20_000; key += 2) {
            assertThat(map.getOrDefault(key, -1)).isEqualTo(key * 3);
        }
    }

    @Test
    void matchesReferenceMapAcrossRandomizedOperations() {
        LongLongHashMap actual = new LongLongHashMap();
        Map<Long, Long> expected = new HashMap<>();
        Random random = new Random(12L);

        for (int i = 0; i < 100_000; i++) {
            long key = 1 + random.nextInt(5_000);
            long value = random.nextLong();
            switch (random.nextInt(3)) {
                case 0 -> {
                    Long previous = expected.put(key, value);
                    long actualPrevious = actual.put(key, value);
                    assertThat(actual.previousPresent()).isEqualTo(previous != null);
                    assertThat(actualPrevious).isEqualTo(previous == null ? 0 : previous);
                }
                case 1 -> {
                    Long previous = expected.remove(key);
                    long actualPrevious = actual.remove(key);
                    assertThat(actual.previousPresent()).isEqualTo(previous != null);
                    assertThat(actualPrevious).isEqualTo(previous == null ? 0 : previous);
                }
                default -> assertThat(actual.getOrDefault(key, Long.MIN_VALUE + 1))
                        .isEqualTo(expected.getOrDefault(key, Long.MIN_VALUE + 1));
            }
        }
    }

    @Test
    void rejectsSentinelAndNonPositiveKeys() {
        LongLongHashMap map = new LongLongHashMap();
        assertThatThrownBy(() -> map.put(0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> map.put(-1, 1)).isInstanceOf(IllegalArgumentException.class);
    }
}

package com.finex.risk;

final class LongLongHashMap {

    private static final long EMPTY = 0;
    private static final long REMOVED = Long.MIN_VALUE;

    private long[] keys;
    private long[] values;
    private int size;
    private int used;
    private int resizeThreshold;
    private boolean previousPresent;

    LongLongHashMap() {
        keys = new long[16];
        values = new long[16];
        resizeThreshold = 10;
    }

    boolean previousPresent() {
        return previousPresent;
    }

    long put(long key, long value) {
        requireKey(key);
        if (used >= resizeThreshold) {
            resize();
        }
        int mask = keys.length - 1;
        int index = index(key, mask);
        int removedIndex = -1;
        while (true) {
            long existing = keys[index];
            if (existing == key) {
                long previous = values[index];
                values[index] = value;
                previousPresent = true;
                return previous;
            }
            if (existing == EMPTY) {
                int target = removedIndex >= 0 ? removedIndex : index;
                keys[target] = key;
                values[target] = value;
                size++;
                if (removedIndex < 0) {
                    used++;
                }
                previousPresent = false;
                return 0;
            }
            if (existing == REMOVED && removedIndex < 0) {
                removedIndex = index;
            }
            index = (index + 1) & mask;
        }
    }

    long remove(long key) {
        requireKey(key);
        int mask = keys.length - 1;
        int index = index(key, mask);
        while (true) {
            long existing = keys[index];
            if (existing == EMPTY) {
                previousPresent = false;
                return 0;
            }
            if (existing == key) {
                long previous = values[index];
                keys[index] = REMOVED;
                values[index] = 0;
                size--;
                previousPresent = true;
                return previous;
            }
            index = (index + 1) & mask;
        }
    }

    long getOrDefault(long key, long defaultValue) {
        requireKey(key);
        int mask = keys.length - 1;
        int index = index(key, mask);
        while (true) {
            long existing = keys[index];
            if (existing == EMPTY) {
                return defaultValue;
            }
            if (existing == key) {
                return values[index];
            }
            index = (index + 1) & mask;
        }
    }

    private void resize() {
        long[] oldKeys = keys;
        long[] oldValues = values;
        keys = new long[oldKeys.length << 1];
        values = new long[keys.length];
        resizeThreshold = keys.length * 5 / 8;
        size = 0;
        used = 0;
        for (int i = 0; i < oldKeys.length; i++) {
            long key = oldKeys[i];
            if (key != EMPTY && key != REMOVED) {
                insertRehashed(key, oldValues[i]);
            }
        }
    }

    private void insertRehashed(long key, long value) {
        int mask = keys.length - 1;
        int index = index(key, mask);
        while (keys[index] != EMPTY) {
            index = (index + 1) & mask;
        }
        keys[index] = key;
        values[index] = value;
        size++;
        used++;
    }

    private static int index(long key, int mask) {
        long mixed = key;
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdl;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53l;
        mixed ^= mixed >>> 33;
        return (int) mixed & mask;
    }

    private static void requireKey(long key) {
        if (key <= 0) {
            throw new IllegalArgumentException("key must be positive");
        }
    }
}

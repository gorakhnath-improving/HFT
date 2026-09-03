package com.finex.risk;

final class LongLongHashMap {

    private static final long EMPTY = 0;
    private static final long REMOVED = Long.MIN_VALUE;

    private long[] keys;
    private long[] values;
    private long[] secondaryValues;
    private long[] tertiaryValues;
    private int size;
    private int used;
    private int resizeThreshold;
    private boolean previousPresent;
    private long previousSecondaryValue;
    private long previousTertiaryValue;

    LongLongHashMap() {
        keys = new long[16];
        values = new long[16];
        secondaryValues = new long[16];
        tertiaryValues = new long[16];
        resizeThreshold = 10;
    }

    boolean previousPresent() {
        return previousPresent;
    }

    long previousSecondaryValue() {
        return previousSecondaryValue;
    }

    long previousTertiaryValue() {
        return previousTertiaryValue;
    }

    long put(long key, long value) {
        return put(key, value, 0, 0);
    }

    long put(long key, long value, long secondaryValue, long tertiaryValue) {
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
                previousSecondaryValue = secondaryValues[index];
                previousTertiaryValue = tertiaryValues[index];
                values[index] = value;
                secondaryValues[index] = secondaryValue;
                tertiaryValues[index] = tertiaryValue;
                previousPresent = true;
                return previous;
            }
            if (existing == EMPTY) {
                int target = removedIndex >= 0 ? removedIndex : index;
                keys[target] = key;
                values[target] = value;
                secondaryValues[target] = secondaryValue;
                tertiaryValues[target] = tertiaryValue;
                size++;
                if (removedIndex < 0) {
                    used++;
                }
                previousPresent = false;
                previousSecondaryValue = 0;
                previousTertiaryValue = 0;
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
                previousSecondaryValue = 0;
                previousTertiaryValue = 0;
                return 0;
            }
            if (existing == key) {
                long previous = values[index];
                previousSecondaryValue = secondaryValues[index];
                previousTertiaryValue = tertiaryValues[index];
                keys[index] = REMOVED;
                values[index] = 0;
                secondaryValues[index] = 0;
                tertiaryValues[index] = 0;
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

    long get(long key) {
        int slot = find(key);
        if (slot < 0) {
            previousPresent = false;
            previousSecondaryValue = 0;
            previousTertiaryValue = 0;
            return 0;
        }
        previousPresent = true;
        previousSecondaryValue = secondaryValues[slot];
        previousTertiaryValue = tertiaryValues[slot];
        return values[slot];
    }

    long secondaryOrDefault(long key, long defaultValue) {
        int slot = find(key);
        return slot < 0 ? defaultValue : secondaryValues[slot];
    }

    long tertiaryOrDefault(long key, long defaultValue) {
        int slot = find(key);
        return slot < 0 ? defaultValue : tertiaryValues[slot];
    }

    private int find(long key) {
        requireKey(key);
        int mask = keys.length - 1;
        int index = index(key, mask);
        while (true) {
            long existing = keys[index];
            if (existing == EMPTY) {
                return -1;
            }
            if (existing == key) {
                return index;
            }
            index = (index + 1) & mask;
        }
    }

    private void resize() {
        long[] oldKeys = keys;
        long[] oldValues = values;
        long[] oldSecondaryValues = secondaryValues;
        long[] oldTertiaryValues = tertiaryValues;
        keys = new long[oldKeys.length << 1];
        values = new long[keys.length];
        secondaryValues = new long[keys.length];
        tertiaryValues = new long[keys.length];
        resizeThreshold = keys.length * 5 / 8;
        size = 0;
        used = 0;
        for (int i = 0; i < oldKeys.length; i++) {
            long key = oldKeys[i];
            if (key != EMPTY && key != REMOVED) {
                insertRehashed(key, oldValues[i], oldSecondaryValues[i], oldTertiaryValues[i]);
            }
        }
    }

    private void insertRehashed(long key, long value, long secondaryValue, long tertiaryValue) {
        int mask = keys.length - 1;
        int index = index(key, mask);
        while (keys[index] != EMPTY) {
            index = (index + 1) & mask;
        }
        keys[index] = key;
        values[index] = value;
        secondaryValues[index] = secondaryValue;
        tertiaryValues[index] = tertiaryValue;
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

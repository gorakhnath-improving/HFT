package com.finex.common.domain.enums;

/**
 * Lifecycle status of an order in the FinEx domain.
 */
public enum OrderStatus {
    NEW,
    OPEN,
    PARTIALLY_FILLED,
    FILLED,
    CANCELLED,
    REJECTED
}

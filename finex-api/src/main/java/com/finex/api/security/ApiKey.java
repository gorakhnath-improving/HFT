package com.finex.api.security;

/**
 * Simple API key tied to a single account. Active keys are the only ones allowed to trade.
 */
public record ApiKey(String key, long accountId, boolean active) {
}

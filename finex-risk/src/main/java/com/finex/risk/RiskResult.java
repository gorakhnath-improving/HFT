package com.finex.risk;

/**
 * Outcome of a pre-trade risk check.
 */
public record RiskResult(boolean accepted, String reason) {

    public static RiskResult ok() {
        return new RiskResult(true, null);
    }

    public static RiskResult reject(String reason) {
        return new RiskResult(false, reason);
    }
}

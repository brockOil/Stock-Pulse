package com.stockpulse.strategy;

/** Which implementation is currently active for a suggestion type; switchable at runtime, no restart. */
public enum StrategyMode {
    RULE_BASED,
    AI
}

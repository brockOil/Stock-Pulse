package com.stockpulse.domain;

/**
 * Which strategy actually produced a suggestion. Distinct from the *configured* active
 * strategy: an AI strategy that fails validation or times out falls back to its rule-based
 * counterpart, and this field records that outcome so it's visible in the UI and API.
 */
public enum RecommendationSource {
    RULE_BASED,
    AI
}

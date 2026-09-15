package com.stockpulse.strategy;

import com.stockpulse.domain.Product;
import com.stockpulse.domain.TriggerReason;

/**
 * Contract shared by every pricing implementation. Both the on-demand HTTP endpoints and the
 * async agentic-loop event handlers call through this same interface without caring which
 * concrete strategy is active (see CommerceStrategyRegistry). A sprint 2 CompetitorAwareStrategy
 * only needs to implement this and register - nothing else changes.
 */
public interface PricingStrategy {
    PricingRecommendation recommend(Product product, TriggerReason triggerReason, double categoryAverageVelocity);
}

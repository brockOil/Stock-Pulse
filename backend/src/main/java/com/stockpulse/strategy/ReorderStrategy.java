package com.stockpulse.strategy;

import com.stockpulse.domain.Product;
import com.stockpulse.domain.TriggerReason;

/** Contract shared by every reorder implementation. See PricingStrategy for the rationale. */
public interface ReorderStrategy {
    ReorderRecommendation recommend(Product product, TriggerReason triggerReason, double categoryAverageVelocity);
}

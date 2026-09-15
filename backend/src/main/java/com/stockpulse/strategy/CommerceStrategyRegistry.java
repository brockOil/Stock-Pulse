package com.stockpulse.strategy;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Single source of truth for which strategy is active per suggestion type. Both the on-demand
 * HTTP endpoints and the async agentic-loop listener resolve the active strategy through this
 * registry, so switching the mode (PATCH /api/config/strategy) takes effect immediately for
 * both callers without a restart or any code change (ADR entry 3).
 */
@Component
public class CommerceStrategyRegistry {

    private final PricingStrategy ruleBasedPricing;
    private final PricingStrategy aiPricing;
    private final ReorderStrategy ruleBasedReorder;
    private final ReorderStrategy aiReorder;

    private final AtomicReference<StrategyMode> pricingMode;
    private final AtomicReference<StrategyMode> reorderMode;

    public CommerceStrategyRegistry(
            @Qualifier("ruleBasedPricingStrategy") PricingStrategy ruleBasedPricing,
            @Qualifier("aiPricingStrategy") PricingStrategy aiPricing,
            @Qualifier("ruleBasedReorderStrategy") ReorderStrategy ruleBasedReorder,
            @Qualifier("aiReorderStrategy") ReorderStrategy aiReorder,
            @Value("${commerce.pricing-strategy}") StrategyMode initialPricingMode,
            @Value("${commerce.reorder-strategy}") StrategyMode initialReorderMode) {
        this.ruleBasedPricing = ruleBasedPricing;
        this.aiPricing = aiPricing;
        this.ruleBasedReorder = ruleBasedReorder;
        this.aiReorder = aiReorder;
        this.pricingMode = new AtomicReference<>(initialPricingMode);
        this.reorderMode = new AtomicReference<>(initialReorderMode);
    }

    public PricingStrategy activePricingStrategy() {
        return pricingMode.get() == StrategyMode.AI ? aiPricing : ruleBasedPricing;
    }

    public ReorderStrategy activeReorderStrategy() {
        return reorderMode.get() == StrategyMode.AI ? aiReorder : ruleBasedReorder;
    }

    public StrategyMode getPricingMode() {
        return pricingMode.get();
    }

    public StrategyMode getReorderMode() {
        return reorderMode.get();
    }

    public void setPricingMode(StrategyMode mode) {
        pricingMode.set(mode);
    }

    public void setReorderMode(StrategyMode mode) {
        reorderMode.set(mode);
    }
}

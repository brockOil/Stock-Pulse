package com.stockpulse.strategy;

import com.stockpulse.domain.ChangeDirection;
import com.stockpulse.domain.Product;
import com.stockpulse.domain.RecommendationSource;
import com.stockpulse.domain.TriggerReason;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Deterministic, dependency-free baseline. Also doubles as the fallback target when the AI
 * strategy fails validation, times out, or the provider is unreachable.
 */
@Component("ruleBasedPricingStrategy")
public class RuleBasedPricingStrategy implements PricingStrategy {

    private final double lowStockIncreasePct;
    private final double demandSpikeIncreasePct;
    private final double demandSpikeMultiplier;

    public RuleBasedPricingStrategy(
            @Value("${commerce.rules.low-stock-increase-pct}") double lowStockIncreasePct,
            @Value("${commerce.rules.demand-spike-increase-pct}") double demandSpikeIncreasePct,
            @Value("${commerce.rules.demand-spike-multiplier}") double demandSpikeMultiplier) {
        this.lowStockIncreasePct = lowStockIncreasePct;
        this.demandSpikeIncreasePct = demandSpikeIncreasePct;
        this.demandSpikeMultiplier = demandSpikeMultiplier;
    }

    @Override
    public PricingRecommendation recommend(Product product, TriggerReason triggerReason, double categoryAverageVelocity) {
        BigDecimal current = product.getCurrentPrice();

        if (product.isBelowReorderThreshold()) {
            BigDecimal recommended = applyPct(current, lowStockIncreasePct);
            String reasoning = "Stock (%d) is below the reorder threshold (%d); increasing price by %.0f%% to protect remaining inventory while a reorder is arranged."
                    .formatted(product.getStockLevel(), product.getReorderThreshold(), lowStockIncreasePct * 100);
            return new PricingRecommendation(recommended, ChangeDirection.INCREASE, 0.75, reasoning, RecommendationSource.RULE_BASED);
        }

        if (categoryAverageVelocity > 0 && product.getDemandVelocity() > demandSpikeMultiplier * categoryAverageVelocity) {
            BigDecimal recommended = applyPct(current, demandSpikeIncreasePct);
            String reasoning = "Demand velocity (%d/24h) is more than %.1fx the %s category average (%.1f/24h); a modest %.0f%% increase captures demand without discouraging the spike."
                    .formatted(product.getDemandVelocity(), demandSpikeMultiplier, product.getCategory(), categoryAverageVelocity, demandSpikeIncreasePct * 100);
            return new PricingRecommendation(recommended, ChangeDirection.INCREASE, 0.70, reasoning, RecommendationSource.RULE_BASED);
        }

        return new PricingRecommendation(current, ChangeDirection.HOLD, 0.60,
                "Stock and demand velocity are both within normal range; no price change recommended.",
                RecommendationSource.RULE_BASED);
    }

    private BigDecimal applyPct(BigDecimal base, double pct) {
        BigDecimal factor = BigDecimal.valueOf(1.0 + pct);
        return base.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }
}

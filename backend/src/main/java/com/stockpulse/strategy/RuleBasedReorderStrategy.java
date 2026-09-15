package com.stockpulse.strategy;

import com.stockpulse.domain.Product;
import com.stockpulse.domain.RecommendationSource;
import com.stockpulse.domain.TriggerReason;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Simple baseline: recommend enough stock to reach (threshold x multiplier), minimum 1 unit. */
@Component("ruleBasedReorderStrategy")
public class RuleBasedReorderStrategy implements ReorderStrategy {

    private final int reorderMultiplier;
    private final int defaultLeadTimeDays;

    public RuleBasedReorderStrategy(
            @Value("${commerce.rules.reorder-multiplier}") int reorderMultiplier,
            @Value("${commerce.rules.default-lead-time-days}") int defaultLeadTimeDays) {
        this.reorderMultiplier = reorderMultiplier;
        this.defaultLeadTimeDays = defaultLeadTimeDays;
    }

    @Override
    public ReorderRecommendation recommend(Product product, TriggerReason triggerReason, double categoryAverageVelocity) {
        int target = product.getReorderThreshold() * reorderMultiplier;
        int quantity = Math.max(1, target - product.getStockLevel());
        String reasoning = "Baseline quantity: (reorder threshold %d x %d) - current stock %d = %d units, minimum 1."
                .formatted(product.getReorderThreshold(), reorderMultiplier, product.getStockLevel(), quantity);
        return new ReorderRecommendation(quantity, defaultLeadTimeDays, 0.65, reasoning, RecommendationSource.RULE_BASED);
    }
}

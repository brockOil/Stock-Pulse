package com.stockpulse.strategy;

import com.stockpulse.domain.RecommendationSource;

public record ReorderRecommendation(
        int recommendedQuantity,
        int leadTimeDays,
        double confidence,
        String reasoning,
        RecommendationSource source
) {
}

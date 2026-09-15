package com.stockpulse.strategy;

import com.stockpulse.domain.ChangeDirection;
import com.stockpulse.domain.RecommendationSource;

import java.math.BigDecimal;

public record PricingRecommendation(
        BigDecimal recommendedPrice,
        ChangeDirection direction,
        double confidence,
        String reasoning,
        RecommendationSource source
) {
}

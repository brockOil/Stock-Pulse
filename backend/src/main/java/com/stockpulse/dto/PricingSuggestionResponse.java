package com.stockpulse.dto;

import com.stockpulse.domain.ChangeDirection;
import com.stockpulse.domain.PricingSuggestion;
import com.stockpulse.domain.RecommendationSource;
import com.stockpulse.domain.SuggestionStatus;
import com.stockpulse.domain.TriggerReason;

import java.math.BigDecimal;
import java.time.Instant;

public record PricingSuggestionResponse(
        String id,
        String productId,
        String productName,
        String productSku,
        BigDecimal currentPriceAtSuggestion,
        BigDecimal recommendedPrice,
        ChangeDirection changeDirection,
        double confidence,
        String reasoning,
        SuggestionStatus status,
        TriggerReason triggerReason,
        RecommendationSource generatedBy,
        Instant createdAt,
        Instant decidedAt
) {
    public static PricingSuggestionResponse from(PricingSuggestion s) {
        return new PricingSuggestionResponse(
                s.getId(), s.getProduct().getId(), s.getProduct().getName(), s.getProduct().getSku(),
                s.getCurrentPriceAtSuggestion(), s.getRecommendedPrice(), s.getChangeDirection(),
                s.getConfidence(), s.getReasoning(), s.getStatus(), s.getTriggerReason(),
                s.getGeneratedBy(), s.getCreatedAt(), s.getDecidedAt()
        );
    }
}

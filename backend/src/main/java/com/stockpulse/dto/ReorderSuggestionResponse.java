package com.stockpulse.dto;

import com.stockpulse.domain.RecommendationSource;
import com.stockpulse.domain.ReorderSuggestion;
import com.stockpulse.domain.SuggestionStatus;
import com.stockpulse.domain.TriggerReason;

import java.time.Instant;

public record ReorderSuggestionResponse(
        String id,
        String productId,
        String productName,
        String productSku,
        int currentStockAtSuggestion,
        int recommendedQuantity,
        int suggestedLeadTimeDays,
        double confidence,
        String reasoning,
        SuggestionStatus status,
        TriggerReason triggerReason,
        RecommendationSource generatedBy,
        Instant createdAt,
        Instant decidedAt
) {
    public static ReorderSuggestionResponse from(ReorderSuggestion s) {
        return new ReorderSuggestionResponse(
                s.getId(), s.getProduct().getId(), s.getProduct().getName(), s.getProduct().getSku(),
                s.getCurrentStockAtSuggestion(), s.getRecommendedQuantity(), s.getSuggestedLeadTimeDays(),
                s.getConfidence(), s.getReasoning(), s.getStatus(), s.getTriggerReason(),
                s.getGeneratedBy(), s.getCreatedAt(), s.getDecidedAt()
        );
    }
}

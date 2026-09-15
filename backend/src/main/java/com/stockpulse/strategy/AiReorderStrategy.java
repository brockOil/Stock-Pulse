package com.stockpulse.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpulse.ai.AiUnavailableException;
import com.stockpulse.ai.JsonExtractor;
import com.stockpulse.ai.LLMGateway;
import com.stockpulse.ai.PromptBuilder;
import com.stockpulse.domain.Product;
import com.stockpulse.domain.RecommendationSource;
import com.stockpulse.domain.TriggerReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AI implementation of the reorder contract. Mirrors AiPricingStrategy: any failure falls back
 * to the rule-based baseline so the async path never silently drops a recommendation.
 */
@Component("aiReorderStrategy")
public class AiReorderStrategy implements ReorderStrategy {

    private static final Logger log = LoggerFactory.getLogger(AiReorderStrategy.class);

    private final LLMGateway gateway;
    private final PromptBuilder promptBuilder;
    private final ReorderStrategy fallback;
    private final ObjectMapper objectMapper;
    private final int defaultLeadTimeDays;
    private final int maxReorderQuantity;

    public AiReorderStrategy(
            LLMGateway gateway,
            PromptBuilder promptBuilder,
            @Qualifier("ruleBasedReorderStrategy") ReorderStrategy fallback,
            ObjectMapper objectMapper,
            @Value("${commerce.rules.default-lead-time-days}") int defaultLeadTimeDays,
            @Value("${commerce.ai.max-reorder-quantity}") int maxReorderQuantity) {
        this.gateway = gateway;
        this.promptBuilder = promptBuilder;
        this.fallback = fallback;
        this.objectMapper = objectMapper;
        this.defaultLeadTimeDays = defaultLeadTimeDays;
        this.maxReorderQuantity = maxReorderQuantity;
    }

    @Override
    public ReorderRecommendation recommend(Product product, TriggerReason triggerReason, double categoryAverageVelocity) {
        try {
            String prompt = promptBuilder.buildReorderPrompt(product, triggerReason, categoryAverageVelocity);
            String raw = gateway.callLLM(prompt);
            JsonNode node = objectMapper.readTree(JsonExtractor.extractJsonObject(raw));

            JsonNode qtyNode = node.path("recommendedQuantity");
            if (!qtyNode.isNumber()) {
                throw new AiUnavailableException("AI response recommendedQuantity is not numeric");
            }
            int quantity = qtyNode.asInt();
            if (quantity <= 0 || quantity > maxReorderQuantity) {
                throw new AiUnavailableException("AI recommended quantity out of bounds: " + quantity);
            }

            int leadTime = node.path("leadTimeDays").asInt(defaultLeadTimeDays);
            if (leadTime <= 0) {
                leadTime = defaultLeadTimeDays;
            }

            double confidence = clamp(node.path("confidence").asDouble(0.5), 0.0, 1.0);
            String reasoning = node.path("reasoning").asText("");
            if (reasoning.isBlank()) {
                throw new AiUnavailableException("AI reorder response missing reasoning");
            }

            return new ReorderRecommendation(quantity, leadTime, confidence, reasoning, RecommendationSource.AI);
        } catch (Exception e) {
            log.warn("AI reorder strategy unavailable for product {} ({}); falling back to rule-based.",
                    product.getId(), e.toString());
            return fallback.recommend(product, triggerReason, categoryAverageVelocity);
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}

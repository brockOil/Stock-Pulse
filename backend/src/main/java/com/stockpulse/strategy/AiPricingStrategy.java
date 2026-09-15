package com.stockpulse.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpulse.ai.AiUnavailableException;
import com.stockpulse.ai.JsonExtractor;
import com.stockpulse.ai.LLMGateway;
import com.stockpulse.ai.PromptBuilder;
import com.stockpulse.domain.ChangeDirection;
import com.stockpulse.domain.Product;
import com.stockpulse.domain.RecommendationSource;
import com.stockpulse.domain.TriggerReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * AI implementation of the pricing contract. Any failure - network, timeout, malformed JSON,
 * or a recommendation outside sane bounds - is caught here and the call transparently
 * delegates to the rule-based strategy so callers (HTTP or async) always get a usable
 * recommendation; a silent drop is worse than a conservative rule-based one (see brief T-4).
 */
@Component("aiPricingStrategy")
public class AiPricingStrategy implements PricingStrategy {

    private static final Logger log = LoggerFactory.getLogger(AiPricingStrategy.class);

    private final LLMGateway gateway;
    private final PromptBuilder promptBuilder;
    private final PricingStrategy fallback;
    private final ObjectMapper objectMapper;
    private final double maxMultiplier;
    private final double minMultiplier;

    public AiPricingStrategy(
            LLMGateway gateway,
            PromptBuilder promptBuilder,
            @Qualifier("ruleBasedPricingStrategy") PricingStrategy fallback,
            ObjectMapper objectMapper,
            @Value("${commerce.ai.price-max-multiplier}") double maxMultiplier,
            @Value("${commerce.ai.price-min-multiplier}") double minMultiplier) {
        this.gateway = gateway;
        this.promptBuilder = promptBuilder;
        this.fallback = fallback;
        this.objectMapper = objectMapper;
        this.maxMultiplier = maxMultiplier;
        this.minMultiplier = minMultiplier;
    }

    @Override
    public PricingRecommendation recommend(Product product, TriggerReason triggerReason, double categoryAverageVelocity) {
        try {
            String prompt = promptBuilder.buildPricingPrompt(product, triggerReason, categoryAverageVelocity);
            String raw = gateway.callLLM(prompt);
            JsonNode node = objectMapper.readTree(JsonExtractor.extractJsonObject(raw));

            BigDecimal recommendedPrice = parsePrice(node);
            ChangeDirection direction = parseDirection(node);
            double confidence = clamp(node.path("confidence").asDouble(0.5), 0.0, 1.0);
            String reasoning = node.path("reasoning").asText("");
            if (reasoning.isBlank()) {
                throw new AiUnavailableException("AI pricing response missing reasoning");
            }

            validateBounds(product.getCurrentPrice(), recommendedPrice);

            return new PricingRecommendation(recommendedPrice, direction, confidence, reasoning, RecommendationSource.AI);
        } catch (Exception e) {
            log.warn("AI pricing strategy unavailable for product {} ({}); falling back to rule-based.",
                    product.getId(), e.toString());
            return fallback.recommend(product, triggerReason, categoryAverageVelocity);
        }
    }

    private BigDecimal parsePrice(JsonNode node) {
        JsonNode priceNode = node.path("recommendedPrice");
        if (!priceNode.isNumber()) {
            throw new AiUnavailableException("AI response recommendedPrice is not numeric");
        }
        return BigDecimal.valueOf(priceNode.asDouble()).setScale(2, RoundingMode.HALF_UP);
    }

    private ChangeDirection parseDirection(JsonNode node) {
        String raw = node.path("direction").asText("");
        try {
            return ChangeDirection.valueOf(raw.toUpperCase(Locale.ROOT).trim());
        } catch (IllegalArgumentException e) {
            throw new AiUnavailableException("AI response direction not recognized: " + raw);
        }
    }

    private void validateBounds(BigDecimal current, BigDecimal recommended) {
        if (recommended.signum() <= 0) {
            throw new AiUnavailableException("AI recommended a non-positive price: " + recommended);
        }
        BigDecimal max = current.multiply(BigDecimal.valueOf(maxMultiplier));
        BigDecimal min = current.multiply(BigDecimal.valueOf(minMultiplier));
        if (recommended.compareTo(max) > 0 || recommended.compareTo(min) < 0) {
            throw new AiUnavailableException(
                    "AI recommended price %s is outside sane bounds [%s, %s] of current price %s"
                            .formatted(recommended, min, max, current));
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}

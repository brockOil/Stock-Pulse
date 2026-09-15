package com.stockpulse.ai;

import com.stockpulse.domain.Product;
import com.stockpulse.domain.TriggerReason;
import org.springframework.stereotype.Component;

/**
 * Builds trigger-aware prompts. INVENTORY_LOW and DEMAND_SPIKE are genuinely different
 * merchandising decisions (see brief T-3 / ADR entry 2) so each gets its own situational
 * framing rather than one generic template with a field swapped in - separately for pricing
 * and for reorder, since "raise price vs. clearance" and "how much to reorder" are different
 * questions even when triggered by the same event.
 */
@Component
public class PromptBuilder {

    public String buildPricingPrompt(Product product, TriggerReason trigger, double categoryAverageVelocity) {
        String situation = switch (trigger) {
            case INVENTORY_LOW -> """
                    SITUATION: Inventory is running low. Stock (%d units) has dropped below the reorder \
                    threshold (%d units). This is a genuinely ambiguous merchandising decision with two valid \
                    strategies: (1) RAISE the price to slow sell-through and protect the remaining units for \
                    higher-value demand while a reorder is placed, or (2) HOLD (or even discount) if the goal is \
                    to clear remaining stock quickly, e.g. ahead of a restock or because the item is being wound \
                    down. Weigh which fits this product and state your reasoning in terms merchandising can act on.\
                    """.formatted(product.getStockLevel(), product.getReorderThreshold());
            case DEMAND_SPIKE -> """
                    SITUATION: Demand has spiked. This product's velocity (%d orders in the last 24h) is well \
                    above the %s category average (%.1f orders in the last 24h). Rising demand against healthy \
                    remaining stock is usually a chance to capture extra margin with a modest price increase, \
                    without pricing the product so high that it kills the momentum driving the spike.\
                    """.formatted(product.getDemandVelocity(), product.getCategory(), categoryAverageVelocity);
            case MANUAL -> """
                    SITUATION: A merchandiser manually requested a pricing review. There is no specific \
                    inventory or demand trigger active right now - assess the product holistically from the \
                    data below.\
                    """;
            case INITIAL -> """
                    SITUATION: This is an initial pricing assessment for a newly catalogued product with no \
                    trigger event yet.\
                    """;
        };

        return """
                You are an AI commerce advisor for an e-commerce merchandising team. Recommend a price for the \
                product below. Respond with ONLY a single minified JSON object - no markdown, no code fences, no \
                commentary outside the JSON - matching exactly this shape:
                {"recommendedPrice": <number>, "direction": "INCREASE"|"DECREASE"|"HOLD", "confidence": <0.0-1.0>, "reasoning": "<1-3 plain-English sentences>"}

                %s

                PRODUCT
                Name: %s
                Category: %s
                Current price: $%s
                Stock level: %d units
                Reorder threshold: %d units
                Demand velocity: %d orders in the last 24h
                Category average demand velocity: %.1f orders in the last 24h
                """.formatted(situation, product.getName(), product.getCategory(), product.getCurrentPrice(),
                product.getStockLevel(), product.getReorderThreshold(), product.getDemandVelocity(), categoryAverageVelocity);
    }

    public String buildReorderPrompt(Product product, TriggerReason trigger, double categoryAverageVelocity) {
        String situation = switch (trigger) {
            case INVENTORY_LOW -> """
                    SITUATION: Stock (%d units) has dropped below the reorder threshold (%d units). Recommend a \
                    replenishment quantity that comfortably covers demand until the next likely reorder point, \
                    without over-ordering stock that moves slowly.\
                    """.formatted(product.getStockLevel(), product.getReorderThreshold());
            case DEMAND_SPIKE -> """
                    SITUATION: Demand has spiked - velocity (%d orders in the last 24h) is well above the %s \
                    category average (%.1f orders in the last 24h). If the spike continues, stock could deplete \
                    faster than the static reorder threshold assumes; size the reorder and lead time with the \
                    elevated velocity in mind, not just the threshold.\
                    """.formatted(product.getDemandVelocity(), product.getCategory(), categoryAverageVelocity);
            case MANUAL -> "SITUATION: A merchandiser manually requested a reorder review with no active trigger.\n";
            case INITIAL -> "SITUATION: Initial reorder assessment for a newly catalogued product.\n";
        };

        return """
                You are an AI inventory planner for an e-commerce merchandising team. Recommend a reorder \
                quantity and lead time for the product below. Respond with ONLY a single minified JSON object - \
                no markdown, no code fences, no commentary outside the JSON - matching exactly this shape:
                {"recommendedQuantity": <positive integer>, "leadTimeDays": <positive integer>, "confidence": <0.0-1.0>, "reasoning": "<1-3 plain-English sentences>"}

                %s

                PRODUCT
                Name: %s
                Category: %s
                Stock level: %d units
                Reorder threshold: %d units
                Demand velocity: %d orders in the last 24h
                Category average demand velocity: %.1f orders in the last 24h
                """.formatted(situation, product.getName(), product.getCategory(),
                product.getStockLevel(), product.getReorderThreshold(), product.getDemandVelocity(), categoryAverageVelocity);
    }
}

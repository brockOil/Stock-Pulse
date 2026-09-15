package com.stockpulse.strategy;

import com.stockpulse.domain.ChangeDirection;
import com.stockpulse.domain.Product;
import com.stockpulse.domain.ProductCategory;
import com.stockpulse.domain.ProductStatus;
import com.stockpulse.domain.TriggerReason;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedPricingStrategyTest {

    private final RuleBasedPricingStrategy strategy = new RuleBasedPricingStrategy(0.10, 0.05, 2.0);

    private Product product(int stock, int threshold, int velocity) {
        Product p = new Product();
        p.setId("PRD-TEST");
        p.setSku("SKU-TEST");
        p.setName("Test Product");
        p.setCategory(ProductCategory.ELECTRONICS);
        p.setCurrentPrice(new BigDecimal("100.00"));
        p.setStockLevel(stock);
        p.setReorderThreshold(threshold);
        p.setDemandVelocity(velocity);
        p.setStatus(ProductStatus.ACTIVE);
        return p;
    }

    @Test
    void recommendsIncreaseWhenBelowReorderThreshold() {
        Product p = product(5, 20, 1);
        PricingRecommendation rec = strategy.recommend(p, TriggerReason.INVENTORY_LOW, 1.0);

        assertThat(rec.direction()).isEqualTo(ChangeDirection.INCREASE);
        assertThat(rec.recommendedPrice()).isEqualByComparingTo("110.00");
    }

    @Test
    void recommendsIncreaseWhenDemandVelocityMoreThanDoubleCategoryAverage() {
        Product p = product(50, 20, 10); // 10 > 2 * 4
        PricingRecommendation rec = strategy.recommend(p, TriggerReason.DEMAND_SPIKE, 4.0);

        assertThat(rec.direction()).isEqualTo(ChangeDirection.INCREASE);
        assertThat(rec.recommendedPrice()).isEqualByComparingTo("105.00");
    }

    @Test
    void holdsWhenStockAndVelocityAreNormal() {
        Product p = product(50, 20, 3);
        PricingRecommendation rec = strategy.recommend(p, TriggerReason.MANUAL, 4.0);

        assertThat(rec.direction()).isEqualTo(ChangeDirection.HOLD);
        assertThat(rec.recommendedPrice()).isEqualByComparingTo("100.00");
    }

    @Test
    void lowStockTakesPriorityOverDemandSpikeCheck() {
        // Both conditions technically true; low-stock branch must win per the spec's if/else-if ordering.
        Product p = product(5, 20, 20);
        PricingRecommendation rec = strategy.recommend(p, TriggerReason.INVENTORY_LOW, 1.0);

        assertThat(rec.recommendedPrice()).isEqualByComparingTo("110.00");
    }
}

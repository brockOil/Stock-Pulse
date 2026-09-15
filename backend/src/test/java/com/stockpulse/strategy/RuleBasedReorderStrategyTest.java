package com.stockpulse.strategy;

import com.stockpulse.domain.Product;
import com.stockpulse.domain.ProductCategory;
import com.stockpulse.domain.ProductStatus;
import com.stockpulse.domain.TriggerReason;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedReorderStrategyTest {

    private final RuleBasedReorderStrategy strategy = new RuleBasedReorderStrategy(3, 7);

    private Product product(int stock, int threshold) {
        Product p = new Product();
        p.setId("PRD-TEST");
        p.setSku("SKU-TEST");
        p.setName("Test Product");
        p.setCategory(ProductCategory.HOME);
        p.setCurrentPrice(new BigDecimal("50.00"));
        p.setStockLevel(stock);
        p.setReorderThreshold(threshold);
        p.setDemandVelocity(1);
        p.setStatus(ProductStatus.ACTIVE);
        return p;
    }

    @Test
    void recommendsThresholdTimesThreeMinusCurrentStock() {
        ReorderRecommendation rec = strategy.recommend(product(8, 15), TriggerReason.INVENTORY_LOW, 1.0);

        // (15 * 3) - 8 = 37
        assertThat(rec.recommendedQuantity()).isEqualTo(37);
        assertThat(rec.leadTimeDays()).isEqualTo(7);
    }

    @Test
    void neverRecommendsLessThanOneUnit() {
        ReorderRecommendation rec = strategy.recommend(product(100, 5), TriggerReason.MANUAL, 1.0);

        assertThat(rec.recommendedQuantity()).isEqualTo(1);
    }
}

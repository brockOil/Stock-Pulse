package com.stockpulse.dto;

import com.stockpulse.domain.Product;
import com.stockpulse.domain.ProductCategory;
import com.stockpulse.domain.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        String id,
        String sku,
        String name,
        ProductCategory category,
        BigDecimal currentPrice,
        int stockLevel,
        int reorderThreshold,
        int demandVelocity,
        ProductStatus status,
        BigDecimal costPrice,
        String supplierId,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProductResponse from(Product p) {
        return new ProductResponse(
                p.getId(), p.getSku(), p.getName(), p.getCategory(), p.getCurrentPrice(),
                p.getStockLevel(), p.getReorderThreshold(), p.getDemandVelocity(), p.getStatus(),
                p.getCostPrice(), p.getSupplierId(), p.getCreatedAt(), p.getUpdatedAt()
        );
    }
}

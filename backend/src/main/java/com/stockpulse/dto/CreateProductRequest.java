package com.stockpulse.dto;

import com.stockpulse.domain.ProductCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Explicit request DTO (never bind the entity directly to a request body) so a client can
 * never set server-managed fields such as id, status, or timestamps.
 */
public record CreateProductRequest(
        @NotBlank @Size(max = 64) String sku,
        @NotBlank @Size(max = 200) String name,
        @NotNull ProductCategory category,
        @NotNull @DecimalMin(value = "0.01", message = "currentPrice must be positive") BigDecimal currentPrice,
        @NotNull @PositiveOrZero Integer stockLevel,
        @NotNull @PositiveOrZero Integer reorderThreshold,
        @PositiveOrZero Integer demandVelocity,
        @DecimalMin(value = "0.00", message = "costPrice must not be negative") BigDecimal costPrice,
        @Size(max = 64) String supplierId
) {
}

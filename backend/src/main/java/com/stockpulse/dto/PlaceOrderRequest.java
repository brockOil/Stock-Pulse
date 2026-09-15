package com.stockpulse.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;

/** quantity is optional; the service defaults to 1 (a single simulated sale) when omitted. */
public record PlaceOrderRequest(
        @Positive @Max(100_000) Integer quantity
) {
}

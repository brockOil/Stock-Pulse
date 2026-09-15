package com.stockpulse.event;

/** Published synchronously (fast, in-request) whenever a stock update leaves a product below its reorder threshold. */
public record InventoryLowEvent(String productId) {
}

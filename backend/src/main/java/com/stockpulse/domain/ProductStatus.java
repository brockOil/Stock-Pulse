package com.stockpulse.domain;

/**
 * ACTIVE - PRICE_REVIEW_PENDING - ACTIVE, with OUT_OF_STOCK entered whenever stock hits zero
 * and left as soon as stock is replenished (subject to any suggestions still pending).
 */
public enum ProductStatus {
    ACTIVE,
    PRICE_REVIEW_PENDING,
    OUT_OF_STOCK
}

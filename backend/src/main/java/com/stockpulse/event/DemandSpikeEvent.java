package com.stockpulse.event;

/** Published synchronously whenever a product's demand velocity crosses the configured spike threshold. */
public record DemandSpikeEvent(String productId) {
}

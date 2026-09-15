package com.stockpulse.domain;

public enum TriggerReason {
    /** Set on the seed data / first suggestion ever created for a product. */
    INITIAL,
    /** Fired by the agentic loop when stock drops below the reorder threshold. */
    INVENTORY_LOW,
    /** Fired by the agentic loop when demand velocity spikes relative to the category average. */
    DEMAND_SPIKE,
    /** A merchandiser explicitly requested a suggestion via the on-demand endpoints. */
    MANUAL
}

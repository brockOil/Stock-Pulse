package com.stockpulse.dto;

import com.stockpulse.strategy.StrategyMode;

public record StrategyConfigResponse(
        StrategyMode pricingStrategy,
        StrategyMode reorderStrategy
) {
}

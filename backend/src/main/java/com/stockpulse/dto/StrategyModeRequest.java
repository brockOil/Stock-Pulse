package com.stockpulse.dto;

import com.stockpulse.strategy.StrategyMode;
import com.stockpulse.strategy.SuggestionType;
import jakarta.validation.constraints.NotNull;

public record StrategyModeRequest(
        @NotNull SuggestionType suggestionType,
        @NotNull StrategyMode mode
) {
}

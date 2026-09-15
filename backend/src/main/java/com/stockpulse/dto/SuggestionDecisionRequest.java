package com.stockpulse.dto;

import com.stockpulse.domain.SuggestionStatus;
import jakarta.validation.constraints.NotNull;

/** status must be ACCEPTED or REJECTED - PENDING is rejected by the service as an invalid transition. */
public record SuggestionDecisionRequest(
        @NotNull SuggestionStatus status
) {
}

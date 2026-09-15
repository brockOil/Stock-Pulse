package com.stockpulse.controller;

import com.stockpulse.dto.StrategyConfigResponse;
import com.stockpulse.dto.StrategyModeRequest;
import com.stockpulse.strategy.CommerceStrategyRegistry;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runtime strategy switch (ADR entry 3): flips the active pricing or reorder strategy between
 * RULE_BASED and AI with no restart and no code change. Both the on-demand suggestion
 * endpoints and the async agentic loop read the active mode from the same registry, so this
 * takes effect immediately for both.
 */
@RestController
@RequestMapping("/api/config/strategy")
public class StrategyConfigController {

    private final CommerceStrategyRegistry registry;

    public StrategyConfigController(CommerceStrategyRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public StrategyConfigResponse current() {
        return new StrategyConfigResponse(registry.getPricingMode(), registry.getReorderMode());
    }

    @PatchMapping
    public StrategyConfigResponse update(@Valid @RequestBody StrategyModeRequest request) {
        switch (request.suggestionType()) {
            case PRICING -> registry.setPricingMode(request.mode());
            case REORDER -> registry.setReorderMode(request.mode());
        }
        return current();
    }
}

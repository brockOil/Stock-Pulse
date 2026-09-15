package com.stockpulse.controller;

import com.stockpulse.domain.SuggestionStatus;
import com.stockpulse.dto.PricingSuggestionResponse;
import com.stockpulse.dto.SuggestionDecisionRequest;
import com.stockpulse.service.SuggestionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/pricing-suggestions")
public class PricingSuggestionController {

    private final SuggestionService suggestionService;

    public PricingSuggestionController(SuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    @GetMapping
    public List<PricingSuggestionResponse> list(
            @RequestParam(required = false) SuggestionStatus status,
            @RequestParam(required = false) String productId) {
        return suggestionService.findPricingSuggestions(productId, status).stream().map(PricingSuggestionResponse::from).toList();
    }

    @PatchMapping("/{id}")
    public PricingSuggestionResponse decide(@PathVariable String id, @Valid @RequestBody SuggestionDecisionRequest request) {
        return PricingSuggestionResponse.from(suggestionService.decidePricingSuggestion(id, request.status()));
    }
}

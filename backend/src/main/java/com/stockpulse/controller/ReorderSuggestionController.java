package com.stockpulse.controller;

import com.stockpulse.domain.SuggestionStatus;
import com.stockpulse.dto.ReorderSuggestionResponse;
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
@RequestMapping("/api/reorder-suggestions")
public class ReorderSuggestionController {

    private final SuggestionService suggestionService;

    public ReorderSuggestionController(SuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    @GetMapping
    public List<ReorderSuggestionResponse> list(
            @RequestParam(required = false) SuggestionStatus status,
            @RequestParam(required = false) String productId) {
        return suggestionService.findReorderSuggestions(productId, status).stream().map(ReorderSuggestionResponse::from).toList();
    }

    @PatchMapping("/{id}")
    public ReorderSuggestionResponse decide(@PathVariable String id, @Valid @RequestBody SuggestionDecisionRequest request) {
        return ReorderSuggestionResponse.from(suggestionService.decideReorderSuggestion(id, request.status()));
    }
}

package com.stockpulse.service;

import com.stockpulse.domain.PricingSuggestion;
import com.stockpulse.domain.Product;
import com.stockpulse.domain.RecommendationSource;
import com.stockpulse.domain.ReorderSuggestion;
import com.stockpulse.domain.SuggestionStatus;
import com.stockpulse.domain.TriggerReason;
import com.stockpulse.exception.InvalidStateException;
import com.stockpulse.exception.ResourceNotFoundException;
import com.stockpulse.repository.PricingSuggestionRepository;
import com.stockpulse.repository.ProductRepository;
import com.stockpulse.repository.ReorderSuggestionRepository;
import com.stockpulse.strategy.CommerceStrategyRegistry;
import com.stockpulse.strategy.PricingRecommendation;
import com.stockpulse.strategy.ReorderRecommendation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Generates and decides both suggestion types through the strategy contracts (T-2/T-3), and
 * is the single place that understands the idempotency rule for the agentic loop: never create
 * a second PENDING suggestion of the same type + trigger for a product that already has one
 * (T-4).
 */
@Service
public class SuggestionService {

    private static final Logger log = LoggerFactory.getLogger(SuggestionService.class);

    private final ProductRepository productRepository;
    private final PricingSuggestionRepository pricingSuggestionRepository;
    private final ReorderSuggestionRepository reorderSuggestionRepository;
    private final CommerceStrategyRegistry strategyRegistry;
    private final ProductLifecycleService lifecycleService;

    public SuggestionService(ProductRepository productRepository,
                              PricingSuggestionRepository pricingSuggestionRepository,
                              ReorderSuggestionRepository reorderSuggestionRepository,
                              CommerceStrategyRegistry strategyRegistry,
                              ProductLifecycleService lifecycleService) {
        this.productRepository = productRepository;
        this.pricingSuggestionRepository = pricingSuggestionRepository;
        this.reorderSuggestionRepository = reorderSuggestionRepository;
        this.strategyRegistry = strategyRegistry;
        this.lifecycleService = lifecycleService;
    }

    @Transactional
    public Optional<PricingSuggestion> generatePricingSuggestion(String productId, TriggerReason trigger) {
        if (pricingSuggestionRepository.existsByProductIdAndTriggerReasonAndStatus(productId, trigger, SuggestionStatus.PENDING)) {
            log.info("Skipping duplicate PENDING pricing suggestion: product={} trigger={}", productId, trigger);
            return Optional.empty();
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        double avgVelocity = productRepository.averagePeerDemandVelocity(product.getCategory(), product.getId());
        PricingRecommendation rec = strategyRegistry.activePricingStrategy().recommend(product, trigger, avgVelocity);

        PricingSuggestion suggestion = new PricingSuggestion();
        suggestion.setId("PS-" + UUID.randomUUID());
        suggestion.setProduct(product);
        suggestion.setCurrentPriceAtSuggestion(product.getCurrentPrice());
        suggestion.setRecommendedPrice(rec.recommendedPrice());
        suggestion.setChangeDirection(rec.direction());
        suggestion.setConfidence(rec.confidence());
        suggestion.setReasoning(rec.reasoning());
        suggestion.setStatus(SuggestionStatus.PENDING);
        suggestion.setTriggerReason(trigger);
        suggestion.setGeneratedBy(rec.source());
        // Capture the returned managed instance: with a manually-assigned String id, Spring
        // Data JPA can route a new entity through merge() rather than persist(), which returns
        // a different managed object than the one passed in - discarding it would silently
        // drop the @PrePersist-populated createdAt/status defaults from the returned suggestion.
        suggestion = pricingSuggestionRepository.save(suggestion);

        lifecycleService.recomputeStatus(product);
        return Optional.of(suggestion);
    }

    @Transactional
    public Optional<ReorderSuggestion> generateReorderSuggestion(String productId, TriggerReason trigger) {
        if (reorderSuggestionRepository.existsByProductIdAndTriggerReasonAndStatus(productId, trigger, SuggestionStatus.PENDING)) {
            log.info("Skipping duplicate PENDING reorder suggestion: product={} trigger={}", productId, trigger);
            return Optional.empty();
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        double avgVelocity = productRepository.averagePeerDemandVelocity(product.getCategory(), product.getId());
        ReorderRecommendation rec = strategyRegistry.activeReorderStrategy().recommend(product, trigger, avgVelocity);

        ReorderSuggestion suggestion = new ReorderSuggestion();
        suggestion.setId("RS-" + UUID.randomUUID());
        suggestion.setProduct(product);
        suggestion.setCurrentStockAtSuggestion(product.getStockLevel());
        suggestion.setRecommendedQuantity(rec.recommendedQuantity());
        suggestion.setSuggestedLeadTimeDays(rec.leadTimeDays());
        suggestion.setConfidence(rec.confidence());
        suggestion.setReasoning(rec.reasoning());
        suggestion.setStatus(SuggestionStatus.PENDING);
        suggestion.setTriggerReason(trigger);
        suggestion.setGeneratedBy(rec.source());
        // See the comment in generatePricingSuggestion: capture save()'s return value.
        suggestion = reorderSuggestionRepository.save(suggestion);

        lifecycleService.recomputeStatus(product);
        return Optional.of(suggestion);
    }

    @Transactional
    public PricingSuggestion decidePricingSuggestion(String suggestionId, SuggestionStatus decision) {
        requireDecidable(decision);
        PricingSuggestion suggestion = pricingSuggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new ResourceNotFoundException("Pricing suggestion not found: " + suggestionId));
        if (suggestion.getStatus() != SuggestionStatus.PENDING) {
            throw new InvalidStateException("Pricing suggestion %s is already %s".formatted(suggestionId, suggestion.getStatus()));
        }

        suggestion.setStatus(decision);
        suggestion.setDecidedAt(Instant.now());

        Product product = suggestion.getProduct();
        if (decision == SuggestionStatus.ACCEPTED) {
            product.setCurrentPrice(suggestion.getRecommendedPrice());
        }
        suggestion = pricingSuggestionRepository.save(suggestion);
        lifecycleService.recomputeStatus(product);
        return suggestion;
    }

    @Transactional
    public ReorderSuggestion decideReorderSuggestion(String suggestionId, SuggestionStatus decision) {
        requireDecidable(decision);
        ReorderSuggestion suggestion = reorderSuggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new ResourceNotFoundException("Reorder suggestion not found: " + suggestionId));
        if (suggestion.getStatus() != SuggestionStatus.PENDING) {
            throw new InvalidStateException("Reorder suggestion %s is already %s".formatted(suggestionId, suggestion.getStatus()));
        }

        suggestion.setStatus(decision);
        suggestion.setDecidedAt(Instant.now());

        Product product = suggestion.getProduct();
        if (decision == SuggestionStatus.ACCEPTED) {
            // Simulated inbound shipment landing.
            product.setStockLevel(product.getStockLevel() + suggestion.getRecommendedQuantity());
        }
        suggestion = reorderSuggestionRepository.save(suggestion);
        lifecycleService.recomputeStatus(product);
        return suggestion;
    }

    @Transactional(readOnly = true)
    public List<PricingSuggestion> findPricingSuggestions(SuggestionStatus status) {
        return status == null
                ? pricingSuggestionRepository.findAllByOrderByCreatedAtDesc()
                : pricingSuggestionRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    @Transactional(readOnly = true)
    public List<ReorderSuggestion> findReorderSuggestions(SuggestionStatus status) {
        return status == null
                ? reorderSuggestionRepository.findAllByOrderByCreatedAtDesc()
                : reorderSuggestionRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    private void requireDecidable(SuggestionStatus decision) {
        if (decision != SuggestionStatus.ACCEPTED && decision != SuggestionStatus.REJECTED) {
            throw new InvalidStateException("status must be ACCEPTED or REJECTED");
        }
    }
}

package com.stockpulse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "reorder_suggestions")
public class ReorderSuggestion {

    @Id
    @Column(length = 64)
    private String id;

    // EAGER for the same reason as PricingSuggestion.product - see that entity's comment.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "current_stock_at_suggestion", nullable = false)
    private int currentStockAtSuggestion;

    @Column(name = "recommended_quantity", nullable = false)
    private int recommendedQuantity;

    @Column(name = "suggested_lead_time_days", nullable = false)
    private int suggestedLeadTimeDays;

    @Column(nullable = false)
    private double confidence;

    @Column(nullable = false, length = 2000)
    private String reasoning;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SuggestionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_reason", nullable = false, length = 20)
    private TriggerReason triggerReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "generated_by", nullable = false, length = 20)
    private RecommendationSource generatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    public ReorderSuggestion() {
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.status == null) {
            this.status = SuggestionStatus.PENDING;
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public int getCurrentStockAtSuggestion() {
        return currentStockAtSuggestion;
    }

    public void setCurrentStockAtSuggestion(int currentStockAtSuggestion) {
        this.currentStockAtSuggestion = currentStockAtSuggestion;
    }

    public int getRecommendedQuantity() {
        return recommendedQuantity;
    }

    public void setRecommendedQuantity(int recommendedQuantity) {
        this.recommendedQuantity = recommendedQuantity;
    }

    public int getSuggestedLeadTimeDays() {
        return suggestedLeadTimeDays;
    }

    public void setSuggestedLeadTimeDays(int suggestedLeadTimeDays) {
        this.suggestedLeadTimeDays = suggestedLeadTimeDays;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public SuggestionStatus getStatus() {
        return status;
    }

    public void setStatus(SuggestionStatus status) {
        this.status = status;
    }

    public TriggerReason getTriggerReason() {
        return triggerReason;
    }

    public void setTriggerReason(TriggerReason triggerReason) {
        this.triggerReason = triggerReason;
    }

    public RecommendationSource getGeneratedBy() {
        return generatedBy;
    }

    public void setGeneratedBy(RecommendationSource generatedBy) {
        this.generatedBy = generatedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }
}

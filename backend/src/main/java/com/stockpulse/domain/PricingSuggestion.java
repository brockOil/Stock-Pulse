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

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "pricing_suggestions")
public class PricingSuggestion {

    @Id
    @Column(length = 64)
    private String id;

    // EAGER: every PricingSuggestionResponse needs the product's name/sku, and suggestions are
    // always read in small, request-scoped lists, so a fetch join here is simpler and safer
    // than lazy-loading across a controller/service transaction boundary.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "current_price_at_suggestion", nullable = false, precision = 12, scale = 2)
    private BigDecimal currentPriceAtSuggestion;

    @Column(name = "recommended_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal recommendedPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_direction", nullable = false, length = 20)
    private ChangeDirection changeDirection;

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

    public PricingSuggestion() {
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

    public BigDecimal getCurrentPriceAtSuggestion() {
        return currentPriceAtSuggestion;
    }

    public void setCurrentPriceAtSuggestion(BigDecimal currentPriceAtSuggestion) {
        this.currentPriceAtSuggestion = currentPriceAtSuggestion;
    }

    public BigDecimal getRecommendedPrice() {
        return recommendedPrice;
    }

    public void setRecommendedPrice(BigDecimal recommendedPrice) {
        this.recommendedPrice = recommendedPrice;
    }

    public ChangeDirection getChangeDirection() {
        return changeDirection;
    }

    public void setChangeDirection(ChangeDirection changeDirection) {
        this.changeDirection = changeDirection;
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

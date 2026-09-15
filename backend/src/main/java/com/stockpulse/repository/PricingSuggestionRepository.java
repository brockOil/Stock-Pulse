package com.stockpulse.repository;

import com.stockpulse.domain.PricingSuggestion;
import com.stockpulse.domain.SuggestionStatus;
import com.stockpulse.domain.TriggerReason;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PricingSuggestionRepository extends JpaRepository<PricingSuggestion, String> {

    boolean existsByProductIdAndTriggerReasonAndStatus(String productId, TriggerReason triggerReason, SuggestionStatus status);

    boolean existsByProductIdAndStatus(String productId, SuggestionStatus status);

    List<PricingSuggestion> findByStatusOrderByCreatedAtDesc(SuggestionStatus status);

    List<PricingSuggestion> findByProductIdOrderByCreatedAtDesc(String productId);

    List<PricingSuggestion> findByProductIdAndStatusOrderByCreatedAtDesc(String productId, SuggestionStatus status);

    List<PricingSuggestion> findAllByOrderByCreatedAtDesc();
}

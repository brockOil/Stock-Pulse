package com.stockpulse.repository;

import com.stockpulse.domain.ReorderSuggestion;
import com.stockpulse.domain.SuggestionStatus;
import com.stockpulse.domain.TriggerReason;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReorderSuggestionRepository extends JpaRepository<ReorderSuggestion, String> {

    boolean existsByProductIdAndTriggerReasonAndStatus(String productId, TriggerReason triggerReason, SuggestionStatus status);

    boolean existsByProductIdAndStatus(String productId, SuggestionStatus status);

    List<ReorderSuggestion> findByStatusOrderByCreatedAtDesc(SuggestionStatus status);

    List<ReorderSuggestion> findByProductIdOrderByCreatedAtDesc(String productId);

    List<ReorderSuggestion> findAllByOrderByCreatedAtDesc();
}

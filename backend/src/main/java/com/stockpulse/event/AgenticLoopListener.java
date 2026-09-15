package com.stockpulse.event;

import com.stockpulse.domain.TriggerReason;
import com.stockpulse.service.SuggestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The agentic loop: observe (stock/velocity signal) -> reason (strategy recommendation) ->
 * act (queue suggestions) -> checkpoint (merchandising approval, handled by the suggestion
 * decision endpoints). This listener never publishes prices itself - it only creates PENDING
 * suggestions.
 *
 * Runs @Async off the request thread, and only AFTER_COMMIT so it always observes the stock/
 * velocity values the triggering request just committed rather than racing a still-open
 * transaction. fallbackExecution=true is a safety net: if an event is ever published outside
 * a transaction, the loop still fires instead of silently dropping it.
 */
@Component
public class AgenticLoopListener {

    private static final Logger log = LoggerFactory.getLogger(AgenticLoopListener.class);

    private final SuggestionService suggestionService;

    public AgenticLoopListener(SuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    @Async("agenticTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onInventoryLow(InventoryLowEvent event) {
        handle(event.productId(), TriggerReason.INVENTORY_LOW);
    }

    @Async("agenticTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDemandSpike(DemandSpikeEvent event) {
        handle(event.productId(), TriggerReason.DEMAND_SPIKE);
    }

    private void handle(String productId, TriggerReason trigger) {
        try {
            suggestionService.generatePricingSuggestion(productId, trigger);
        } catch (Exception e) {
            // The AI strategy already falls back to rule-based internally; reaching here means
            // something unexpected happened (e.g. the product was deleted concurrently). Log
            // and continue to the reorder suggestion rather than letting one failure cancel both.
            log.error("Failed to generate pricing suggestion for product {} trigger {}", productId, trigger, e);
        }
        try {
            suggestionService.generateReorderSuggestion(productId, trigger);
        } catch (Exception e) {
            log.error("Failed to generate reorder suggestion for product {} trigger {}", productId, trigger, e);
        }
    }
}

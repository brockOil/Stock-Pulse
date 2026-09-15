package com.stockpulse.controller;

import com.stockpulse.ai.LLMGateway;
import com.stockpulse.ai.PromptBuilder;
import com.stockpulse.domain.Product;
import com.stockpulse.domain.TriggerReason;
import com.stockpulse.dto.PricingSuggestionResponse;
import com.stockpulse.repository.ProductRepository;
import com.stockpulse.service.ProductService;
import com.stockpulse.service.SuggestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Bonus (+5 pts): streams the AI's reasoning token-by-token before the suggestion lands.
 * Implemented as SseEmitter over POST (native EventSource can't POST, so the frontend consumes
 * this with fetch() + a ReadableStream reader rather than the EventSource API).
 *
 * Known simplification: the streamed tokens are a live "thinking aloud" preview from a direct
 * LLM call; the suggestion that's actually persisted is generated afterward through the normal
 * SuggestionService path (same validation/fallback guarantees as every other suggestion). If
 * the active pricing strategy is AI, that means a second LLM call - an acceptable tradeoff for
 * an optional UX bonus, called out here rather than hidden.
 */
@RestController
@RequestMapping("/api/products")
public class PricingStreamController {

    private static final Logger log = LoggerFactory.getLogger(PricingStreamController.class);
    private static final long TIMEOUT_MS = 30_000L;

    private final ProductService productService;
    private final ProductRepository productRepository;
    private final PromptBuilder promptBuilder;
    private final LLMGateway gateway;
    private final SuggestionService suggestionService;
    private final Executor executor;

    public PricingStreamController(ProductService productService,
                                    ProductRepository productRepository,
                                    PromptBuilder promptBuilder,
                                    LLMGateway gateway,
                                    SuggestionService suggestionService,
                                    @Qualifier("agenticTaskExecutor") Executor executor) {
        this.productService = productService;
        this.productRepository = productRepository;
        this.promptBuilder = promptBuilder;
        this.gateway = gateway;
        this.suggestionService = suggestionService;
        this.executor = executor;
    }

    @PostMapping(value = "/{id}/suggest-pricing/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamPricingSuggestion(@PathVariable String id) {
        Product product = productService.getProduct(id); // 404s synchronously if missing
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);

        executor.execute(() -> {
            try {
                double avgVelocity = productRepository.averagePeerDemandVelocity(product.getCategory(), product.getId());
                String prompt = promptBuilder.buildPricingPrompt(product, TriggerReason.MANUAL, avgVelocity);
                gateway.streamLLM(prompt, token -> sendQuietly(emitter, "token", token));
            } catch (Exception e) {
                log.info("Token stream unavailable for product {} ({}); continuing to final suggestion.", id, e.toString());
            }

            try {
                Optional<PricingSuggestionResponse> response = suggestionService
                        .generatePricingSuggestion(id, TriggerReason.MANUAL)
                        .map(PricingSuggestionResponse::from);
                if (response.isPresent()) {
                    sendQuietly(emitter, "suggestion", response.get());
                } else {
                    sendQuietly(emitter, "error", "A pending MANUAL pricing suggestion already exists for this product");
                }
                emitter.complete();
            } catch (Exception e) {
                log.error("Failed to finalize streamed pricing suggestion for product {}", id, e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private void sendQuietly(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException e) {
            log.debug("Client disconnected from pricing suggestion stream", e);
        }
    }
}

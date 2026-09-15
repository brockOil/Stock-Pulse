package com.stockpulse.controller;

import com.stockpulse.domain.ProductCategory;
import com.stockpulse.domain.ProductStatus;
import com.stockpulse.domain.SuggestionStatus;
import com.stockpulse.domain.TriggerReason;
import com.stockpulse.dto.CreateProductRequest;
import com.stockpulse.dto.PricingSuggestionResponse;
import com.stockpulse.dto.ProductResponse;
import com.stockpulse.dto.SuggestionDecisionRequest;
import com.stockpulse.dto.UpdateStockRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end proof of the agentic loop: a stock update that drops a product below its reorder
 * threshold must - asynchronously, without the caller waiting - produce a PENDING pricing
 * suggestion tagged INVENTORY_LOW, and accepting it must update the product's live price.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AgenticLoopIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @BeforeEach
    void useJdkHttpClient() {
        // The default HttpURLConnection-backed factory rejects PATCH outright; the JDK
        // HttpClient-backed one (Spring 6.1+) supports it natively.
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @Test
    void stockDropBelowThresholdQueuesPendingSuggestions() {
        CreateProductRequest create = new CreateProductRequest(
                "SKU-TEST-" + System.nanoTime(), "Agentic Loop Test Widget", ProductCategory.ELECTRONICS,
                new BigDecimal("100.00"), 15, 10, 1, null, null);
        ResponseEntity<ProductResponse> created = rest.postForEntity("/api/products", create, ProductResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String productId = created.getBody().id();

        // Drop stock below the reorder threshold (10). The endpoint must return immediately;
        // suggestion generation happens off-thread.
        ResponseEntity<ProductResponse> stockUpdate = rest.exchange(
                "/api/products/" + productId + "/stock", HttpMethod.PATCH,
                new HttpEntity<>(new UpdateStockRequest(5)), ProductResponse.class);
        assertThat(stockUpdate.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stockUpdate.getBody().stockLevel()).isEqualTo(5);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            ProductResponse product = rest.getForObject("/api/products/" + productId, ProductResponse.class);
            assertThat(product.status()).isEqualTo(ProductStatus.PRICE_REVIEW_PENDING);
        });

        List<PricingSuggestionResponse> pending = List.of(
                rest.getForObject("/api/pricing-suggestions?status=PENDING", PricingSuggestionResponse[].class));
        Optional<PricingSuggestionResponse> suggestion = pending.stream()
                .filter(s -> s.productId().equals(productId) && s.triggerReason() == TriggerReason.INVENTORY_LOW)
                .findFirst();
        assertThat(suggestion).isPresent();

        ResponseEntity<PricingSuggestionResponse> decided = rest.exchange(
                "/api/pricing-suggestions/" + suggestion.get().id(), HttpMethod.PATCH,
                new HttpEntity<>(new SuggestionDecisionRequest(SuggestionStatus.ACCEPTED)),
                PricingSuggestionResponse.class);
        assertThat(decided.getStatusCode()).isEqualTo(HttpStatus.OK);

        ProductResponse afterAccept = rest.getForObject("/api/products/" + productId, ProductResponse.class);
        assertThat(afterAccept.currentPrice()).isEqualByComparingTo(decided.getBody().recommendedPrice());
    }
}

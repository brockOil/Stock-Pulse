package com.stockpulse.controller;

import com.stockpulse.domain.Product;
import com.stockpulse.domain.ProductCategory;
import com.stockpulse.domain.ProductStatus;
import com.stockpulse.domain.TriggerReason;
import com.stockpulse.dto.CreateProductRequest;
import com.stockpulse.dto.PlaceOrderRequest;
import com.stockpulse.dto.PricingSuggestionResponse;
import com.stockpulse.dto.ProductResponse;
import com.stockpulse.dto.ReorderSuggestionResponse;
import com.stockpulse.dto.UpdateStockRequest;
import com.stockpulse.exception.InvalidStateException;
import com.stockpulse.service.ProductService;
import com.stockpulse.service.SuggestionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final SuggestionService suggestionService;

    public ProductController(ProductService productService, SuggestionService suggestionService) {
        this.productService = productService;
        this.suggestionService = suggestionService;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        Product product = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductResponse.from(product));
    }

    @GetMapping
    public List<ProductResponse> listProducts(
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) ProductCategory category) {
        return productService.listProducts(status, category).stream().map(ProductResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ProductResponse getProduct(@PathVariable String id) {
        return ProductResponse.from(productService.getProduct(id));
    }

    @PatchMapping("/{id}/stock")
    public ProductResponse updateStock(@PathVariable String id, @Valid @RequestBody UpdateStockRequest request) {
        return ProductResponse.from(productService.updateStock(id, request.stockLevel()));
    }

    @PostMapping("/{id}/orders")
    public ProductResponse placeOrder(@PathVariable String id, @RequestBody(required = false) PlaceOrderRequest request) {
        int quantity = (request == null || request.quantity() == null) ? 1 : request.quantity();
        return ProductResponse.from(productService.placeOrder(id, quantity));
    }

    @PostMapping("/{id}/suggest-pricing")
    public ResponseEntity<PricingSuggestionResponse> suggestPricing(@PathVariable String id) {
        productService.getProduct(id); // 404s cleanly if the product doesn't exist
        PricingSuggestionResponse response = suggestionService.generatePricingSuggestion(id, TriggerReason.MANUAL)
                .map(PricingSuggestionResponse::from)
                .orElseThrow(() -> new InvalidStateException(
                        "A pending MANUAL pricing suggestion already exists for product " + id));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/suggest-reorder")
    public ResponseEntity<ReorderSuggestionResponse> suggestReorder(@PathVariable String id) {
        productService.getProduct(id);
        ReorderSuggestionResponse response = suggestionService.generateReorderSuggestion(id, TriggerReason.MANUAL)
                .map(ReorderSuggestionResponse::from)
                .orElseThrow(() -> new InvalidStateException(
                        "A pending MANUAL reorder suggestion already exists for product " + id));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

package com.stockpulse.service;

import com.stockpulse.domain.Product;
import com.stockpulse.domain.ProductStatus;
import com.stockpulse.domain.SuggestionStatus;
import com.stockpulse.repository.PricingSuggestionRepository;
import com.stockpulse.repository.ProductRepository;
import com.stockpulse.repository.ReorderSuggestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the Product.status state machine (ACTIVE - PRICE_REVIEW_PENDING - ACTIVE, with
 * OUT_OF_STOCK whenever stock is zero) so the transition rule lives in exactly one place -
 * both ProductService (stock/order changes) and SuggestionService (suggestion creation and
 * decisions) call in here rather than mutating status directly.
 */
@Service
public class ProductLifecycleService {

    private final ProductRepository productRepository;
    private final PricingSuggestionRepository pricingSuggestionRepository;
    private final ReorderSuggestionRepository reorderSuggestionRepository;

    public ProductLifecycleService(ProductRepository productRepository,
                                    PricingSuggestionRepository pricingSuggestionRepository,
                                    ReorderSuggestionRepository reorderSuggestionRepository) {
        this.productRepository = productRepository;
        this.pricingSuggestionRepository = pricingSuggestionRepository;
        this.reorderSuggestionRepository = reorderSuggestionRepository;
    }

    @Transactional
    public void recomputeStatus(Product product) {
        if (product.isOutOfStock()) {
            product.setStatus(ProductStatus.OUT_OF_STOCK);
        } else {
            boolean anyPending = pricingSuggestionRepository.existsByProductIdAndStatus(product.getId(), SuggestionStatus.PENDING)
                    || reorderSuggestionRepository.existsByProductIdAndStatus(product.getId(), SuggestionStatus.PENDING);
            product.setStatus(anyPending ? ProductStatus.PRICE_REVIEW_PENDING : ProductStatus.ACTIVE);
        }
        productRepository.save(product);
    }
}

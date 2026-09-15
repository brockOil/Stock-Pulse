package com.stockpulse.service;

import com.stockpulse.domain.Product;
import com.stockpulse.domain.ProductCategory;
import com.stockpulse.domain.ProductStatus;
import com.stockpulse.dto.CreateProductRequest;
import com.stockpulse.event.DemandSpikeEvent;
import com.stockpulse.event.InventoryLowEvent;
import com.stockpulse.exception.DuplicateResourceException;
import com.stockpulse.exception.ResourceNotFoundException;
import com.stockpulse.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final ProductLifecycleService lifecycleService;
    private final ApplicationEventPublisher eventPublisher;
    private final double demandSpikeMultiplier;
    private final int demandSpikeMinAbsolute;

    public ProductService(ProductRepository productRepository,
                           ProductLifecycleService lifecycleService,
                           ApplicationEventPublisher eventPublisher,
                           @Value("${commerce.trigger.demand-spike-multiplier}") double demandSpikeMultiplier,
                           @Value("${commerce.trigger.demand-spike-min-absolute}") int demandSpikeMinAbsolute) {
        this.productRepository = productRepository;
        this.lifecycleService = lifecycleService;
        this.eventPublisher = eventPublisher;
        this.demandSpikeMultiplier = demandSpikeMultiplier;
        this.demandSpikeMinAbsolute = demandSpikeMinAbsolute;
    }

    @Transactional
    public Product createProduct(CreateProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new DuplicateResourceException("A product with SKU '" + request.sku() + "' already exists");
        }
        Product product = new Product();
        product.setId("PRD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        product.setSku(request.sku());
        product.setName(request.name());
        product.setCategory(request.category());
        product.setCurrentPrice(request.currentPrice());
        product.setStockLevel(request.stockLevel());
        product.setReorderThreshold(request.reorderThreshold());
        product.setDemandVelocity(request.demandVelocity() == null ? 0 : request.demandVelocity());
        product.setCostPrice(request.costPrice());
        product.setSupplierId(request.supplierId());
        product.setStatus(product.isOutOfStock() ? ProductStatus.OUT_OF_STOCK : ProductStatus.ACTIVE);
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public List<Product> listProducts(ProductStatus status, ProductCategory category) {
        if (status != null && category != null) {
            return productRepository.findByStatusAndCategory(status, category);
        }
        if (status != null) {
            return productRepository.findByStatus(status);
        }
        if (category != null) {
            return productRepository.findByCategory(category);
        }
        return productRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Product getProduct(String id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    /** Absolute stock correction (e.g. a manual recount or inbound shipment applied directly). */
    @Transactional
    public Product updateStock(String id, int newStockLevel) {
        Product product = getProduct(id);
        product.setStockLevel(newStockLevel);
        lifecycleService.recomputeStatus(product);
        evaluateTriggers(product);
        return product;
    }

    /** Simulates a sale: decrements stock (floored at zero) and bumps demand velocity. */
    @Transactional
    public Product placeOrder(String id, int quantity) {
        Product product = getProduct(id);
        int decrement = Math.min(quantity, product.getStockLevel());
        product.setStockLevel(product.getStockLevel() - decrement);
        product.setDemandVelocity(product.getDemandVelocity() + quantity);
        lifecycleService.recomputeStatus(product);
        evaluateTriggers(product);
        return product;
    }

    /**
     * Fires the agentic loop. Events are published synchronously here (cheap - just handing
     * off to the event bus) but handled by an @Async @TransactionalEventListener that only
     * runs after this transaction commits, so the listener always reads committed stock/
     * velocity values. The endpoint itself returns immediately; suggestion generation happens
     * off the request thread (see AgenticLoopListener).
     */
    private void evaluateTriggers(Product product) {
        if (product.isBelowReorderThreshold()) {
            log.debug("Product {} below reorder threshold ({} < {}); publishing InventoryLowEvent",
                    product.getId(), product.getStockLevel(), product.getReorderThreshold());
            eventPublisher.publishEvent(new InventoryLowEvent(product.getId()));
        }

        double avgVelocity = productRepository.averagePeerDemandVelocity(product.getCategory(), product.getId());
        boolean overRatio = avgVelocity > 0 && product.getDemandVelocity() > demandSpikeMultiplier * avgVelocity;
        boolean overFloor = product.getDemandVelocity() >= demandSpikeMinAbsolute;
        if (overRatio && overFloor) {
            log.debug("Product {} demand velocity spike ({} vs category avg {}); publishing DemandSpikeEvent",
                    product.getId(), product.getDemandVelocity(), avgVelocity);
            eventPublisher.publishEvent(new DemandSpikeEvent(product.getId()));
        }
    }
}

package com.stockpulse.repository;

import com.stockpulse.domain.Product;
import com.stockpulse.domain.ProductCategory;
import com.stockpulse.domain.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, String> {

    boolean existsBySku(String sku);

    List<Product> findByStatusAndCategory(ProductStatus status, ProductCategory category);

    List<Product> findByStatus(ProductStatus status);

    List<Product> findByCategory(ProductCategory category);

    /**
     * Average demand velocity of a product's category PEERS (every other product in the same
     * category, excluding the product itself), used as the baseline the agentic loop compares
     * a single product's velocity against to detect a demand spike.
     *
     * Excluding self matters: including the product being evaluated in its own baseline makes
     * a spike mathematically unreachable whenever the category size equals the spike
     * multiplier (v > multiplier * avg reduces to v * (n - multiplier) > multiplier * sum of
     * others, which is never satisfiable when n == multiplier, regardless of v) - exactly the
     * case for the seeded 3-product APPAREL category at the default 3.0x multiplier.
     */
    @Query("select coalesce(avg(p.demandVelocity), 0) from Product p where p.category = :category and p.id <> :excludedProductId")
    double averagePeerDemandVelocity(@Param("category") ProductCategory category, @Param("excludedProductId") String excludedProductId);

    Optional<Product> findBySku(String sku);
}

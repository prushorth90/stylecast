package com.stylecast.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    Optional<Product> findByIdAndActiveTrue(UUID id);

    /**
     * Used by {@code com.stylecast.recommendation} to load candidate
     * products for one outfit-template category slot at a time.
     */
    List<Product> findByCategoryAndActiveTrue(ProductCategory category);
}

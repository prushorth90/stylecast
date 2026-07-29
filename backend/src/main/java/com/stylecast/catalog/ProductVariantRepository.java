package com.stylecast.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Used directly by tests to verify seeded variant counts; production code
 * reads variants through the {@link Product#getVariants()} association.
 */
public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    List<ProductVariant> findByProductId(UUID productId);
}

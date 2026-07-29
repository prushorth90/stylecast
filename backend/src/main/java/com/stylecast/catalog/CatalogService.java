package com.stylecast.catalog;

import com.stylecast.catalog.dto.ProductDetailResponse;
import com.stylecast.catalog.dto.ProductPageResponse;
import com.stylecast.catalog.dto.ProductSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Application service for catalog product search and retrieval. Holds the
 * filter-validation and specification-composition rules that don't belong
 * in the controller.
 *
 * Methods are read-only transactional so lazily-loaded variant/tag
 * collections (mapped with {@code @BatchSize}) can be read while building
 * response DTOs, without the controller ever touching JPA entities.
 */
@Service
@Transactional(readOnly = true)
public class CatalogService {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;
    static final int MIN_FORMALITY = 1;
    static final int MAX_FORMALITY = 10;

    private final ProductRepository productRepository;

    public CatalogService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public ProductPageResponse listProducts(ProductSearchCriteria criteria, int page, int pageSize) {
        validateCriteria(criteria);
        int effectivePageSize = validatePageSize(pageSize);
        if (page < 0) {
            throw new InvalidProductFilterException("page must not be negative");
        }

        Specification<Product> spec = buildSpecification(criteria);
        Pageable pageable = PageRequest.of(page, effectivePageSize, Sort.by("name").ascending().and(Sort.by("id").ascending()));

        Page<Product> result = productRepository.findAll(spec, pageable);
        // Accessing variants/tags here is intentional: each collection is
        // mapped with @BatchSize, so Hibernate loads them for the whole page
        // in one extra query per collection type, not one query per product.
        List<ProductSummaryResponse> content = result.getContent().stream()
                .map(ProductSummaryResponse::fromEntity)
                .toList();

        return new ProductPageResponse(content, page, effectivePageSize, result.getTotalElements(), result.getTotalPages());
    }

    public ProductDetailResponse getProduct(UUID productId) {
        Product product = productRepository.findByIdAndActiveTrue(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        return ProductDetailResponse.fromEntity(product);
    }

    private void validateCriteria(ProductSearchCriteria criteria) {
        if (criteria.maxPrice() != null && criteria.maxPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidProductFilterException("maxPrice must not be negative");
        }
        if (criteria.minimumFormality() != null && !isValidFormality(criteria.minimumFormality())) {
            throw new InvalidProductFilterException(
                    "minimumFormality must be between " + MIN_FORMALITY + " and " + MAX_FORMALITY);
        }
        if (criteria.maximumFormality() != null && !isValidFormality(criteria.maximumFormality())) {
            throw new InvalidProductFilterException(
                    "maximumFormality must be between " + MIN_FORMALITY + " and " + MAX_FORMALITY);
        }
        if (criteria.minimumFormality() != null && criteria.maximumFormality() != null
                && criteria.minimumFormality() > criteria.maximumFormality()) {
            throw new InvalidProductFilterException("minimumFormality must not exceed maximumFormality");
        }
    }

    private boolean isValidFormality(int formality) {
        return formality >= MIN_FORMALITY && formality <= MAX_FORMALITY;
    }

    private int validatePageSize(int pageSize) {
        if (pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        if (pageSize > MAX_PAGE_SIZE) {
            throw new InvalidProductFilterException("pageSize must not exceed " + MAX_PAGE_SIZE);
        }
        return pageSize;
    }

    private Specification<Product> buildSpecification(ProductSearchCriteria criteria) {
        Specification<Product> spec = ProductSpecifications.isActive();

        if (criteria.category() != null) {
            spec = spec.and(ProductSpecifications.hasCategory(criteria.category()));
        }
        if (criteria.maxPrice() != null) {
            spec = spec.and(ProductSpecifications.basePriceAtMost(criteria.maxPrice()));
        }
        if (criteria.minimumFormality() != null) {
            spec = spec.and(ProductSpecifications.formalityAtLeast(criteria.minimumFormality()));
        }
        if (criteria.maximumFormality() != null) {
            spec = spec.and(ProductSpecifications.formalityAtMost(criteria.maximumFormality()));
        }
        if (criteria.occasion() != null) {
            spec = spec.and(ProductSpecifications.hasOccasionTag(criteria.occasion()));
        }
        if (criteria.preferredStyle() != null) {
            spec = spec.and(ProductSpecifications.hasStyleTag(criteria.preferredStyle()));
        }
        if (criteria.weather() != null) {
            spec = spec.and(ProductSpecifications.hasWeatherTag(criteria.weather()));
        }
        if (criteria.clothingSize() != null || criteria.color() != null || Boolean.TRUE.equals(criteria.inStock())) {
            spec = spec.and(ProductSpecifications.matchesVariant(criteria.clothingSize(), criteria.color(), criteria.inStock()));
        }

        return spec;
    }
}

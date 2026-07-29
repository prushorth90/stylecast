package com.stylecast.catalog;

import com.stylecast.catalog.dto.ProductDetailResponse;
import com.stylecast.catalog.dto.ProductPageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Thin controller for catalog product listing/filtering and retrieval. All
 * business rules live in {@link CatalogService}.
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final CatalogService catalogService;

    public ProductController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public ProductPageResponse listProducts(
            @RequestParam(required = false) ProductCategory category,
            @RequestParam(required = false) String clothingSize,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) StyleTag preferredStyle,
            @RequestParam(required = false) OccasionTag occasion,
            @RequestParam(required = false) WeatherTag weather,
            @RequestParam(required = false) Integer minimumFormality,
            @RequestParam(required = false) Integer maximumFormality,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {

        ProductSearchCriteria criteria = new ProductSearchCriteria(
                category, clothingSize, color, maxPrice, preferredStyle, occasion, weather,
                minimumFormality, maximumFormality, inStock);

        return catalogService.listProducts(criteria, page, pageSize);
    }

    @GetMapping("/{productId}")
    public ProductDetailResponse getProduct(@PathVariable UUID productId) {
        return catalogService.getProduct(productId);
    }
}

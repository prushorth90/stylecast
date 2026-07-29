package com.stylecast.catalog.dto;

import com.stylecast.catalog.OccasionTag;
import com.stylecast.catalog.Product;
import com.stylecast.catalog.ProductCategory;
import com.stylecast.catalog.ProductVariant;
import com.stylecast.catalog.StyleTag;
import com.stylecast.catalog.WeatherTag;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Public API representation of a {@link Product} for list/search results.
 * Intentionally lighter than {@link ProductDetailResponse}: it summarizes
 * variants (available sizes/colors, starting price, in-stock) rather than
 * listing every variant.
 */
public record ProductSummaryResponse(
        UUID id,
        String brand,
        String name,
        ProductCategory category,
        BigDecimal startingPrice,
        String imageUrl,
        int formalityLevel,
        Set<String> availableSizes,
        Set<String> availableColors,
        Set<OccasionTag> occasionTags,
        Set<StyleTag> styleTags,
        Set<WeatherTag> weatherTags,
        boolean inStock
) {
    public static ProductSummaryResponse fromEntity(Product product) {
        Set<String> sizes = new LinkedHashSet<>();
        Set<String> colors = new LinkedHashSet<>();
        boolean inStock = false;
        BigDecimal startingPrice = product.getBasePrice();

        for (ProductVariant variant : product.getVariants()) {
            sizes.add(variant.getClothingSize());
            colors.add(variant.getColor());
            if (variant.isInStock()) {
                inStock = true;
            }
            BigDecimal effectivePrice = variant.getEffectivePrice();
            if (effectivePrice.compareTo(startingPrice) < 0) {
                startingPrice = effectivePrice;
            }
        }

        return new ProductSummaryResponse(
                product.getId(),
                product.getBrand(),
                product.getName(),
                product.getCategory(),
                startingPrice,
                product.getImageUrl(),
                product.getFormalityLevel(),
                sizes,
                colors,
                sortedCopy(product.getOccasionTags(), Comparator.comparing(Enum::name)),
                sortedCopy(product.getStyleTags(), Comparator.comparing(Enum::name)),
                sortedCopy(product.getWeatherTags(), Comparator.comparing(Enum::name)),
                inStock);
    }

    private static <T> Set<T> sortedCopy(Set<T> source, Comparator<T> comparator) {
        Set<T> copy = new java.util.TreeSet<>(comparator);
        copy.addAll(source);
        return copy;
    }
}

package com.stylecast.recommendation;

import com.stylecast.catalog.ProductCategory;
import com.stylecast.recommendation.LiveOutfitAssembler.LiveAssembledOutfit;
import com.stylecast.recommendation.LiveOutfitAssembler.LiveSelectedItem;
import com.stylecast.recommendation.dto.LiveOutfitItemResponse;
import com.stylecast.recommendation.dto.LiveOutfitRecommendationResponse;
import com.stylecast.recommendation.dto.LiveRecommendationsResponse;
import com.stylecast.retail.ProductSearchProviderException;
import com.stylecast.retail.RetailProductCandidate;
import com.stylecast.retail.RetailProductSearchRequest;
import com.stylecast.retail.RetailProductSearchService;
import com.stylecast.retail.RetailProductSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application service orchestrating live outfit generation for an event:
 *
 * <pre>
 * RecommendationContextLoader -> LiveCategorySearchRequestFactory
 *   -> RetailProductSearchService (one call per required category, independently)
 *   -> LiveOutfitAssembler -> persist -> respond
 * </pre>
 *
 * <p>Every candidate comes from {@code com.stylecast.retail}'s live
 * Nordstrom product-search provider - this service has no dependency on
 * {@code com.stylecast.catalog} and never substitutes local/fictional
 * products. Each required category is searched independently: a {@link
 * ProductSearchProviderException} from one category's search is caught and
 * that category is simply treated as missing - it never discards candidates
 * already found for other categories, and never aborts the whole attempt.
 * The overall {@link LiveRecommendationCompleteness} reflects this:
 * {@code COMPLETE} (every category found something), {@code PARTIAL} (some
 * did, some didn't - valid candidates for the successful categories are
 * still returned), {@code NO_RESULTS} (every category was searched
 * successfully but found nothing), or {@code PROVIDER_UNAVAILABLE} (every
 * attempted search failed at the provider level - a transient outage).
 */
@Service
public class LiveRecommendationService {

    private final RecommendationContextLoader contextLoader;
    private final LiveCategorySearchRequestFactory requestFactory;
    private final RetailProductSearchService retailSearchService;
    private final LiveOutfitAssembler assembler;
    private final LiveOutfitRecommendationRepository repository;

    public LiveRecommendationService(
            RecommendationContextLoader contextLoader,
            LiveCategorySearchRequestFactory requestFactory,
            RetailProductSearchService retailSearchService,
            LiveOutfitAssembler assembler,
            LiveOutfitRecommendationRepository repository) {
        this.contextLoader = contextLoader;
        this.requestFactory = requestFactory;
        this.retailSearchService = retailSearchService;
        this.assembler = assembler;
        this.repository = repository;
    }

    /**
     * Searches every required category (independently) and persists the
     * result as a new generation. Throws {@link
     * com.stylecast.event.EventNotFoundException} (404), {@link
     * MissingStylePreferencesException} (409), or {@link
     * MissingOccasionInterpretationException} (409) if a prerequisite is
     * missing. Never throws for a live-search failure - see {@link
     * LiveRecommendationCompleteness#PROVIDER_UNAVAILABLE}.
     */
    @Transactional
    public LiveRecommendationsResponse generate(UUID eventId) {
        RecommendationContext context = contextLoader.load(eventId);
        List<ProductCategory> requiredCategories = context.requiredCategories();
        return executeAndPersist(eventId, context, requiredCategories, requiredCategories, new EnumMap<>(ProductCategory.class));
    }

    /**
     * Re-searches only the categories the latest generation was missing,
     * reusing the candidates already found for every other category (no
     * repeated search calls for categories that already succeeded) - bounds
     * the added API cost of a retry to just the gap. A no-op (no search
     * calls at all, current state returned unchanged) when the latest
     * generation had no missing categories, or nothing has been generated
     * yet a fresh {@link #generate} is performed instead.
     */
    @Transactional
    public LiveRecommendationsResponse retryMissing(UUID eventId) {
        RecommendationContext context = contextLoader.load(eventId);
        List<ProductCategory> requiredCategories = context.requiredCategories();

        Optional<LiveOutfitRecommendation> latest = repository.findFirstByEventIdOrderByGenerationDesc(eventId);
        if (latest.isEmpty()) {
            return executeAndPersist(eventId, context, requiredCategories, requiredCategories, new EnumMap<>(ProductCategory.class));
        }

        int latestGeneration = latest.get().getGeneration();
        List<LiveOutfitRecommendation> latestRows = repository
                .findByEventIdAndStatusInOrderByRankPositionAsc(eventId, List.of(RecommendationStatus.ACTIVE, RecommendationStatus.NO_VALID_OUTFIT))
                .stream()
                .filter(row -> row.getGeneration() == latestGeneration)
                .toList();

        List<ProductCategory> previousMissing = latestRows.isEmpty() ? requiredCategories : latestRows.get(0).getMissingCategories();
        if (previousMissing.isEmpty()) {
            // Nothing missing to retry - avoid an unnecessary live-search call.
            return buildResponse(eventId);
        }

        Map<ProductCategory, List<RetailProductCandidate>> seedCandidates = reconstructFoundCandidates(latestRows);
        return executeAndPersist(eventId, context, requiredCategories, previousMissing, seedCandidates);
    }

    /**
     * Returns the event's current (latest generation) live recommendations
     * without generating anything or calling the live provider - repeated
     * calls never re-run a search.
     */
    @Transactional(readOnly = true)
    public LiveRecommendationsResponse getCurrent(UUID eventId) {
        contextLoader.requireEvent(eventId);
        return buildResponse(eventId);
    }

    /**
     * Shared core for {@link #generate} and {@link #retryMissing}: builds a
     * request per required category (so the per-category budget split is
     * always based on the full required-category count, even on a retry),
     * but only actually executes a search for categories in {@code
     * categoriesToSearch} - {@code seedCandidates} supplies the rest
     * (already-found candidates being reused, or an empty map for a fresh
     * {@link #generate}).
     */
    private LiveRecommendationsResponse executeAndPersist(
            UUID eventId, RecommendationContext context, List<ProductCategory> requiredCategories,
            List<ProductCategory> categoriesToSearch, Map<ProductCategory, List<RetailProductCandidate>> seedCandidates) {

        Map<ProductCategory, RetailProductSearchRequest> requestByCategory = new EnumMap<>(ProductCategory.class);
        Map<ProductCategory, List<RetailProductCandidate>> candidatesByCategory = new EnumMap<>(ProductCategory.class);
        candidatesByCategory.putAll(seedCandidates);

        int attempted = 0;
        int errored = 0;
        for (RetailProductSearchRequest request : requestFactory.buildRequests(context, requiredCategories)) {
            requestByCategory.put(request.category(), request);
            if (!categoriesToSearch.contains(request.category())) {
                continue;
            }
            attempted++;
            try {
                candidatesByCategory.put(request.category(), retailSearchService.search(request).candidates());
            } catch (ProductSearchProviderException e) {
                // Caught per-category, never aborts the whole attempt - candidates already
                // found for other categories are preserved regardless of this failure.
                errored++;
                candidatesByCategory.putIfAbsent(request.category(), List.of());
            }
        }

        List<ProductCategory> found = assembler.foundCategories(candidatesByCategory, requiredCategories);
        List<ProductCategory> missing = assembler.categoriesWithNoCandidates(candidatesByCategory, requiredCategories);
        boolean allAttemptsFailed = attempted > 0 && errored == attempted;

        LiveRecommendationCompleteness completeness;
        if (found.isEmpty() && allAttemptsFailed) {
            completeness = LiveRecommendationCompleteness.PROVIDER_UNAVAILABLE;
        } else if (found.isEmpty()) {
            completeness = LiveRecommendationCompleteness.NO_RESULTS;
        } else if (missing.isEmpty()) {
            completeness = LiveRecommendationCompleteness.COMPLETE;
        } else {
            completeness = LiveRecommendationCompleteness.PARTIAL;
        }
        String message = buildMessage(completeness, found, missing);

        List<LiveAssembledOutfit> assemblies = assembler.assemble(candidatesByCategory, requiredCategories);

        Instant now = Instant.now();
        int nextGeneration = repository.findFirstByEventIdOrderByGenerationDesc(eventId)
                .map(r -> r.getGeneration() + 1)
                .orElse(1);
        supersedeActiveRecommendations(eventId, now);

        if (assemblies.isEmpty()) {
            repository.save(LiveOutfitRecommendation.withoutOutfit(eventId, nextGeneration, completeness, found, missing, message, now));
        } else {
            int rank = 1;
            for (LiveAssembledOutfit outfit : assemblies) {
                persistOutfit(eventId, nextGeneration, rank, outfit, requestByCategory, completeness, found, missing, message, now);
                rank++;
            }
        }

        return buildResponse(eventId);
    }

    /**
     * Reconstructs each category's already-found candidates from the latest
     * generation's persisted items (grouped by category, deduplicated by
     * product URL) so {@link #retryMissing} never re-searches a category
     * that already succeeded.
     */
    private Map<ProductCategory, List<RetailProductCandidate>> reconstructFoundCandidates(List<LiveOutfitRecommendation> latestRows) {
        Map<ProductCategory, List<RetailProductCandidate>> byCategory = new EnumMap<>(ProductCategory.class);
        for (LiveOutfitRecommendation row : latestRows) {
            if (row.getStatus() != RecommendationStatus.ACTIVE) {
                continue;
            }
            for (LiveOutfitItem item : row.getItems()) {
                List<RetailProductCandidate> candidates = byCategory.computeIfAbsent(item.getCategory(), c -> new ArrayList<>());
                RetailProductCandidate candidate = toCandidate(item);
                boolean alreadyPresent = candidates.stream().anyMatch(c -> c.productUrl().equals(candidate.productUrl()));
                if (!alreadyPresent) {
                    candidates.add(candidate);
                }
            }
        }
        return byCategory;
    }

    private RetailProductCandidate toCandidate(LiveOutfitItem item) {
        return new RetailProductCandidate(
                RetailProductSource.AI_WEB_SEARCH,
                item.getRetailer(),
                item.getTitle(),
                item.getBrand(),
                item.getCategory(),
                item.getPrice(),
                item.getOriginalPrice(),
                item.getCurrency(),
                item.getProductUrl(),
                item.getImageUrl(),
                null,
                item.getColor(),
                item.getAvailableSizes(),
                item.getStockText(),
                item.isPriceVerified(),
                item.isSizeVerified(),
                item.isAvailabilityVerified(),
                item.getAudience(),
                item.getCreatedAt(),
                item.getSourceCitation());
    }

    private void persistOutfit(
            UUID eventId, int generation, int rank, LiveAssembledOutfit outfit,
            Map<ProductCategory, RetailProductSearchRequest> requestByCategory, LiveRecommendationCompleteness completeness,
            List<ProductCategory> found, List<ProductCategory> missing, String message, Instant now) {
        String name = "Live Look " + rank;
        String explanation = buildExplanation(outfit);
        LiveOutfitRecommendation recommendation = LiveOutfitRecommendation.active(
                eventId, generation, rank, name, explanation, completeness, found, missing, message, now);

        int displayOrder = 0;
        for (LiveSelectedItem item : outfit.items()) {
            RetailProductCandidate candidate = item.candidate();
            String requestedSize = requestByCategory.get(item.category()).clothingSize();
            recommendation.addItem(new LiveOutfitItem(
                    UUID.randomUUID(),
                    item.category(),
                    candidate.retailer(),
                    candidate.title(),
                    candidate.brand(),
                    candidate.productUrl(),
                    candidate.imageUrl(),
                    candidate.price(),
                    candidate.originalPrice(),
                    candidate.currency(),
                    candidate.priceVerified(),
                    candidate.color(),
                    requestedSize,
                    candidate.availableSizes(),
                    candidate.sizeVerified(),
                    candidate.stockText(),
                    candidate.availabilityVerified(),
                    candidate.audience(),
                    candidate.sourceCitation(),
                    displayOrder++,
                    now));
        }
        repository.save(recommendation);
    }

    private void supersedeActiveRecommendations(UUID eventId, Instant now) {
        List<LiveOutfitRecommendation> active = repository.findByEventIdAndStatus(eventId, RecommendationStatus.ACTIVE);
        active.forEach(recommendation -> recommendation.supersede(now));
        repository.saveAll(active);
    }

    private String buildMessage(LiveRecommendationCompleteness completeness, List<ProductCategory> found, List<ProductCategory> missing) {
        return switch (completeness) {
            case COMPLETE -> null;
            case PARTIAL -> "We found items for " + formatCategoryList(found) + ", but no matching "
                    + formatCategoryList(missing) + ".";
            case NO_RESULTS -> "No live Nordstrom products were found for required categor"
                    + (missing.size() == 1 ? "y: " : "ies: ") + formatCategoryList(missing) + ".";
            case PROVIDER_UNAVAILABLE -> "Live Nordstrom search is temporarily unavailable. Please try again shortly.";
        };
    }

    private String formatCategoryList(List<ProductCategory> categories) {
        List<String> labels = categories.stream().map(this::formatCategoryLabel).toList();
        if (labels.size() == 1) {
            return labels.get(0);
        }
        if (labels.size() == 2) {
            return labels.get(0) + " and " + labels.get(1);
        }
        return String.join(", ", labels.subList(0, labels.size() - 1)) + ", and " + labels.get(labels.size() - 1);
    }

    private String formatCategoryLabel(ProductCategory category) {
        String lower = category.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private String buildExplanation(LiveAssembledOutfit outfit) {
        String categories = outfit.items().stream().map(item -> item.category().name()).collect(Collectors.joining(", "));
        return "Includes " + outfit.items().size() + " live nordstrom.com product(s) (" + categories + ").";
    }

    private LiveRecommendationsResponse buildResponse(UUID eventId) {
        Optional<LiveOutfitRecommendation> latest = repository.findFirstByEventIdOrderByGenerationDesc(eventId);
        if (latest.isEmpty()) {
            return LiveRecommendationsResponse.notGeneratedYet(eventId);
        }

        int generation = latest.get().getGeneration();
        List<LiveOutfitRecommendation> rows = repository
                .findByEventIdAndStatusInOrderByRankPositionAsc(eventId, List.of(RecommendationStatus.ACTIVE, RecommendationStatus.NO_VALID_OUTFIT))
                .stream()
                .filter(row -> row.getGeneration() == generation)
                .toList();

        LiveOutfitRecommendation summary = rows.isEmpty() ? latest.get() : rows.get(0);

        List<LiveOutfitRecommendationResponse> recommendations = rows.stream()
                .filter(row -> row.getStatus() == RecommendationStatus.ACTIVE)
                .map(this::toResponse)
                .toList();

        return new LiveRecommendationsResponse(
                eventId, generation, summary.getGeneratedAt(), summary.getCompleteness(),
                summary.getFoundCategories(), summary.getMissingCategories(), summary.getMessage(), recommendations);
    }

    private LiveOutfitRecommendationResponse toResponse(LiveOutfitRecommendation recommendation) {
        List<LiveOutfitItemResponse> items = recommendation.getItems().stream().map(this::toResponse).toList();
        return new LiveOutfitRecommendationResponse(
                recommendation.getId(),
                recommendation.getEventId(),
                recommendation.getGeneration(),
                recommendation.getRankPosition(),
                recommendation.getName(),
                recommendation.getStatus(),
                recommendation.getSource(),
                recommendation.getExplanation(),
                recommendation.getGeneratedAt(),
                items);
    }

    private LiveOutfitItemResponse toResponse(LiveOutfitItem item) {
        return new LiveOutfitItemResponse(
                item.getId(),
                item.getCategory(),
                item.getRetailer(),
                item.getTitle(),
                item.getBrand(),
                item.getProductUrl(),
                item.getImageUrl(),
                item.getPrice(),
                item.getOriginalPrice(),
                item.getCurrency(),
                item.isPriceVerified(),
                item.getColor(),
                item.getRequestedSize(),
                item.getAvailableSizes(),
                item.isSizeVerified(),
                item.getStockText(),
                item.isAvailabilityVerified(),
                item.getAudience(),
                item.getSourceCitation(),
                item.getDisplayOrder());
    }
}

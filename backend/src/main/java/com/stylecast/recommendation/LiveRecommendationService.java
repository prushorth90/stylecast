package com.stylecast.recommendation;

import com.stylecast.catalog.ProductCategory;
import com.stylecast.occasion.RequestedItem;
import com.stylecast.recommendation.LiveOutfitAssembler.LiveAssembledOutfit;
import com.stylecast.recommendation.LiveOutfitAssembler.LiveSelectedItem;
import com.stylecast.recommendation.RequestedItemSearchRequestFactory.RequestedItemSearchRequest;
import com.stylecast.recommendation.dto.LiveOutfitItemResponse;
import com.stylecast.recommendation.dto.LiveOutfitRecommendationResponse;
import com.stylecast.recommendation.dto.LiveRecommendationsResponse;
import com.stylecast.retail.ProductSearchProviderException;
import com.stylecast.retail.RetailProductCandidate;
import com.stylecast.retail.RetailProductSearchRequest;
import com.stylecast.retail.RetailProductSearchService;
import com.stylecast.retail.RetailProductSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Application service orchestrating live outfit generation for an event.
 * Two parallel pipelines exist, and an event uses exactly one per
 * generation, chosen by {@link RecommendationContext#requestedItems()}:
 *
 * <pre>
 * Explicit-item pipeline (Task 8.5, tried first):
 *   RecommendationContextLoader -&gt; RequestedItemSearchRequestFactory
 *     -&gt; RetailProductSearchService (one call per explicit RequestedItem)
 *     -&gt; LiveOutfitAssembler.assembleFromItems -&gt; persist -&gt; respond
 *
 * Category-template pipeline (Task 8, fallback when no explicit items exist):
 *   RecommendationContextLoader -&gt; LiveCategorySearchRequestFactory
 *     -&gt; RetailProductSearchService (one call per required category)
 *     -&gt; LiveOutfitAssembler.assemble -&gt; persist -&gt; respond
 * </pre>
 *
 * <p>Whenever an event's occasion interpretation extracted explicit product
 * phrases (e.g. "USA soccer jersey"), those take priority over the
 * interpretation's broad {@code requiredCategories} - the category-template
 * pipeline is only used when no explicit items exist at all (including
 * every interpretation generated before Task 8.5 existed, which always has
 * an empty requested-items list).
 *
 * <p>Every candidate comes from {@code com.stylecast.retail}'s live
 * Nordstrom product-search provider - this service has no dependency on
 * {@code com.stylecast.catalog} and never substitutes local/fictional
 * products, and never substitutes an unrelated candidate (from another
 * category or another requested item) just because it shares a broad
 * category. Each required category/requested item is searched
 * independently: a {@link ProductSearchProviderException} from one is
 * caught and that one is simply treated as missing - it never discards
 * candidates already found for others, and never aborts the whole attempt.
 * The overall {@link LiveRecommendationCompleteness} reflects this:
 * {@code COMPLETE} (everything found something), {@code PARTIAL} (some
 * did, some didn't - valid candidates for the successful ones are still
 * returned), {@code NO_RESULTS} (everything was searched successfully but
 * found nothing), or {@code PROVIDER_UNAVAILABLE} (every attempted search
 * errored at the provider level - a transient outage).
 */
@Service
public class LiveRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(LiveRecommendationService.class);

    private final RecommendationContextLoader contextLoader;
    private final LiveCategorySearchRequestFactory requestFactory;
    private final RequestedItemSearchRequestFactory requestedItemRequestFactory;
    private final RetailProductSearchService retailSearchService;
    private final LiveOutfitAssembler assembler;
    private final LiveOutfitRecommendationRepository repository;

    public LiveRecommendationService(
            RecommendationContextLoader contextLoader,
            LiveCategorySearchRequestFactory requestFactory,
            RequestedItemSearchRequestFactory requestedItemRequestFactory,
            RetailProductSearchService retailSearchService,
            LiveOutfitAssembler assembler,
            LiveOutfitRecommendationRepository repository) {
        this.contextLoader = contextLoader;
        this.requestFactory = requestFactory;
        this.requestedItemRequestFactory = requestedItemRequestFactory;
        this.retailSearchService = retailSearchService;
        this.assembler = assembler;
        this.repository = repository;
    }

    /**
     * Validates that {@code eventId} exists and has both saved styling
     * preferences and an occasion interpretation, WITHOUT performing any
     * live search - throws the same {@link com.stylecast.event.EventNotFoundException}
     * (404)/{@link MissingStylePreferencesException} (409)/{@link
     * MissingOccasionInterpretationException} (409) that {@link #generate}
     * would, but fast (no OpenAI call). Used by {@link
     * LiveRecommendationJobService#startGenerateJob} so an invalid request
     * is rejected synchronously rather than only surfacing as a later
     * {@link LiveGenerationJobStatus#FAILED} job.
     */
    @Transactional(readOnly = true)
    public void validatePrerequisites(UUID eventId) {
        contextLoader.load(eventId);
    }

    /**
     * Searches every explicit requested item (if any exist), otherwise
     * every required category, and persists the result as a new
     * generation. Throws {@link com.stylecast.event.EventNotFoundException}
     * (404), {@link MissingStylePreferencesException} (409), or {@link
     * MissingOccasionInterpretationException} (409) if a prerequisite is
     * missing. Never throws for a live-search failure - see {@link
     * LiveRecommendationCompleteness#PROVIDER_UNAVAILABLE}.
     */
    @Transactional
    public LiveRecommendationsResponse generate(UUID eventId) {
        RecommendationContext context = contextLoader.load(eventId);
        return generateFresh(eventId, context, context.requestedItems());
    }

    private LiveRecommendationsResponse generateFresh(UUID eventId, RecommendationContext context, List<RequestedItem> requestedItems) {
        if (!requestedItems.isEmpty()) {
            return executeAndPersistForItems(eventId, context, requestedItems, requestedItems, new LinkedHashMap<>());
        }
        List<ProductCategory> requiredCategories = context.requiredCategories();
        return executeAndPersist(eventId, context, requiredCategories, requiredCategories, new EnumMap<>(ProductCategory.class));
    }

    /**
     * Re-searches only what the latest generation was missing (whether that
     * generation used the explicit-item or category-template pipeline),
     * reusing everything already found - bounds the added API cost of a
     * retry to just the gap. A no-op (current state returned unchanged)
     * when nothing was missing, or a fresh {@link #generate} when nothing
     * has been generated yet.
     */
    @Transactional
    public LiveRecommendationsResponse retryMissing(UUID eventId) {
        RecommendationContext context = contextLoader.load(eventId);
        List<RequestedItem> requestedItems = context.requestedItems();

        Optional<LiveOutfitRecommendation> latest = repository.findFirstByEventIdOrderByGenerationDesc(eventId);
        if (latest.isEmpty()) {
            return generateFresh(eventId, context, requestedItems);
        }

        int latestGeneration = latest.get().getGeneration();
        List<LiveOutfitRecommendation> latestRows = repository
                .findByEventIdAndStatusInOrderByRankPositionAsc(eventId, List.of(RecommendationStatus.ACTIVE, RecommendationStatus.NO_VALID_OUTFIT))
                .stream()
                .filter(row -> row.getGeneration() == latestGeneration)
                .toList();
        LiveOutfitRecommendation summaryRow = latestRows.isEmpty() ? latest.get() : latestRows.get(0);

        if (!requestedItems.isEmpty()) {
            List<RequestedItemSummary> previousMissingSummaries = summaryRow.getMissingRequestedItems();
            if (previousMissingSummaries.isEmpty()) {
                // Nothing missing to retry - avoid an unnecessary live-search call.
                return buildResponse(eventId);
            }
            Set<UUID> missingIds = previousMissingSummaries.stream().map(RequestedItemSummary::id).collect(Collectors.toSet());
            List<RequestedItem> previousMissingItems = requestedItems.stream()
                    .filter(item -> missingIds.contains(item.id()))
                    .toList();
            if (previousMissingItems.isEmpty()) {
                // The interpretation changed since the last generation (different item ids) -
                // nothing can be safely retried; return the current state unchanged.
                return buildResponse(eventId);
            }
            Map<UUID, List<RetailProductCandidate>> seedCandidates = reconstructFoundCandidatesByItem(latestRows);
            return executeAndPersistForItems(eventId, context, requestedItems, previousMissingItems, seedCandidates);
        }

        List<ProductCategory> requiredCategories = context.requiredCategories();
        List<ProductCategory> previousMissing = summaryRow.getMissingCategories();
        if (previousMissing.isEmpty()) {
            return buildResponse(eventId);
        }
        Map<ProductCategory, List<RetailProductCandidate>> seedCandidates = reconstructFoundCandidates(latestRows);
        return executeAndPersist(eventId, context, requiredCategories, previousMissing, seedCandidates);
    }

    /**
     * Marks the event's latest-generation recommendation row(s) (whether
     * {@link RecommendationStatus#ACTIVE} or {@link
     * RecommendationStatus#NO_VALID_OUTFIT}) as {@link
     * LiveOutfitRecommendation#isStale() stale}, WITHOUT calling the live
     * search provider or creating a new generation - used by the event
     * setup flow when saved styling preferences changed in a way that
     * makes the previously generated looks (based on the old preferences/
     * interpretation) potentially outdated. A no-op when nothing has been
     * generated yet for this event.
     */
    @Transactional
    public void invalidateStaleRecommendations(UUID eventId) {
        contextLoader.requireEvent(eventId);

        Optional<LiveOutfitRecommendation> latest = repository.findFirstByEventIdOrderByGenerationDesc(eventId);
        if (latest.isEmpty()) {
            return;
        }
        int latestGeneration = latest.get().getGeneration();
        List<LiveOutfitRecommendation> latestRows = repository
                .findByEventIdAndStatusInOrderByRankPositionAsc(eventId, List.of(RecommendationStatus.ACTIVE, RecommendationStatus.NO_VALID_OUTFIT))
                .stream()
                .filter(row -> row.getGeneration() == latestGeneration)
                .toList();

        Instant now = Instant.now();
        latestRows.forEach(row -> row.markStale(now));
        repository.saveAll(latestRows);
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

    // ---------- Category-template pipeline (Task 8, unchanged behavior) ----------

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
        LiveRecommendationCompleteness completeness = deriveCompleteness(found.isEmpty(), missing.isEmpty(), allAttemptsFailed);
        String message = buildCategoryMessage(completeness, found, missing);

        List<LiveAssembledOutfit> assemblies = assembler.assemble(candidatesByCategory, requiredCategories);

        Instant now = Instant.now();
        int nextGeneration = nextGeneration(eventId);
        supersedeActiveRecommendations(eventId, now);

        if (assemblies.isEmpty()) {
            repository.save(LiveOutfitRecommendation.withoutOutfit(
                    eventId, nextGeneration, completeness, found, missing, List.of(), List.of(), message, now));
        } else {
            int rank = 1;
            for (LiveAssembledOutfit outfit : assemblies) {
                Function<LiveSelectedItem, String> sizeResolver =
                        item -> requestByCategory.get(item.category()).clothingSize();
                persistOutfit(eventId, nextGeneration, rank, outfit, sizeResolver,
                        completeness, found, missing, List.of(), List.of(), message, now);
                rank++;
            }
        }

        return buildResponse(eventId);
    }

    /**
     * Reconstructs each category's already-found candidates from the latest
     * generation's persisted items (grouped by category, deduplicated by
     * product URL) so a category-based {@link #retryMissing} never
     * re-searches a category that already succeeded.
     */
    private Map<ProductCategory, List<RetailProductCandidate>> reconstructFoundCandidates(List<LiveOutfitRecommendation> latestRows) {
        Map<ProductCategory, List<RetailProductCandidate>> byCategory = new EnumMap<>(ProductCategory.class);
        for (LiveOutfitRecommendation row : latestRows) {
            if (row.getStatus() != RecommendationStatus.ACTIVE) {
                continue;
            }
            for (LiveOutfitItem item : row.getItems()) {
                if (item.getCategory() == null) {
                    continue;
                }
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

    // ---------- Explicit-item pipeline (Task 8.5) ----------

    private LiveRecommendationsResponse executeAndPersistForItems(
            UUID eventId, RecommendationContext context, List<RequestedItem> allItems,
            List<RequestedItem> itemsToSearch, Map<UUID, List<RetailProductCandidate>> seedCandidates) {

        Map<UUID, RetailProductSearchRequest> requestByItemId = new LinkedHashMap<>();
        Map<UUID, List<RetailProductCandidate>> candidatesByItemId = new LinkedHashMap<>(seedCandidates);

        int attempted = 0;
        int errored = 0;
        for (RequestedItemSearchRequest requestedItemRequest : requestedItemRequestFactory.buildRequests(context, allItems)) {
            RequestedItem item = requestedItemRequest.item();
            requestByItemId.put(item.id(), requestedItemRequest.request());
            if (!containsItemId(itemsToSearch, item.id())) {
                continue;
            }
            attempted++;
            try {
                candidatesByItemId.put(item.id(), retailSearchService.search(requestedItemRequest.request()).candidates());
            } catch (ProductSearchProviderException e) {
                // Caught per-item, never aborts the whole attempt, and never substitutes an
                // unrelated candidate for this item - it simply remains missing.
                errored++;
                candidatesByItemId.putIfAbsent(item.id(), List.of());
            }
        }

        List<RequestedItem> found = assembler.foundItems(candidatesByItemId, allItems);
        List<RequestedItem> missing = assembler.itemsWithNoCandidates(candidatesByItemId, allItems);
        boolean allAttemptsFailed = attempted > 0 && errored == attempted;
        LiveRecommendationCompleteness completeness = deriveCompleteness(found.isEmpty(), missing.isEmpty(), allAttemptsFailed);
        String message = buildItemMessage(completeness, found, missing);

        List<LiveAssembledOutfit> assemblies = assembler.assembleFromItems(candidatesByItemId, allItems);

        List<RequestedItemSummary> foundSummaries = found.stream().map(RequestedItemSummary::from).toList();
        List<RequestedItemSummary> missingSummaries = missing.stream().map(RequestedItemSummary::from).toList();

        Instant now = Instant.now();
        int nextGeneration = nextGeneration(eventId);
        supersedeActiveRecommendations(eventId, now);

        if (assemblies.isEmpty()) {
            repository.save(LiveOutfitRecommendation.withoutOutfit(
                    eventId, nextGeneration, completeness, List.of(), List.of(), foundSummaries, missingSummaries, message, now));
        } else {
            int rank = 1;
            for (LiveAssembledOutfit outfit : assemblies) {
                Function<LiveSelectedItem, String> sizeResolver =
                        item -> requestByItemId.get(item.requestedItem().id()).clothingSize();
                persistOutfit(eventId, nextGeneration, rank, outfit, sizeResolver,
                        completeness, List.of(), List.of(), foundSummaries, missingSummaries, message, now);
                rank++;
            }
        }

        return buildResponse(eventId);
    }

    private boolean containsItemId(List<RequestedItem> items, UUID itemId) {
        return items.stream().anyMatch(item -> item.id().equals(itemId));
    }

    /**
     * Reconstructs each requested item's already-found candidates from the
     * latest generation's persisted items (grouped by {@code
     * requestedItemId}, deduplicated by product URL) so an item-based
     * {@link #retryMissing} never re-searches an item that already
     * succeeded.
     */
    private Map<UUID, List<RetailProductCandidate>> reconstructFoundCandidatesByItem(List<LiveOutfitRecommendation> latestRows) {
        Map<UUID, List<RetailProductCandidate>> byItemId = new LinkedHashMap<>();
        for (LiveOutfitRecommendation row : latestRows) {
            if (row.getStatus() != RecommendationStatus.ACTIVE) {
                continue;
            }
            for (LiveOutfitItem item : row.getItems()) {
                if (item.getRequestedItemId() == null) {
                    continue;
                }
                List<RetailProductCandidate> candidates = byItemId.computeIfAbsent(item.getRequestedItemId(), id -> new ArrayList<>());
                RetailProductCandidate candidate = toCandidate(item);
                boolean alreadyPresent = candidates.stream().anyMatch(c -> c.productUrl().equals(candidate.productUrl()));
                if (!alreadyPresent) {
                    candidates.add(candidate);
                }
            }
        }
        return byItemId;
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

    // ---------- Shared persistence/response building ----------

    private LiveRecommendationCompleteness deriveCompleteness(boolean foundIsEmpty, boolean missingIsEmpty, boolean allAttemptsFailed) {
        if (foundIsEmpty && allAttemptsFailed) {
            return LiveRecommendationCompleteness.PROVIDER_UNAVAILABLE;
        } else if (foundIsEmpty) {
            return LiveRecommendationCompleteness.NO_RESULTS;
        } else if (missingIsEmpty) {
            return LiveRecommendationCompleteness.COMPLETE;
        } else {
            return LiveRecommendationCompleteness.PARTIAL;
        }
    }

    private int nextGeneration(UUID eventId) {
        return repository.findFirstByEventIdOrderByGenerationDesc(eventId).map(r -> r.getGeneration() + 1).orElse(1);
    }

    /**
     * Persists one assembled outfit's items - shared by both pipelines
     * since {@link LiveSelectedItem} already carries either a {@code
     * category} or a {@code requestedItem} (never both), which is exactly
     * the signal used here to decide which {@link LiveOutfitItem}
     * constructor/columns to populate.
     */
    private void persistOutfit(
            UUID eventId, int generation, int rank, LiveAssembledOutfit outfit, Function<LiveSelectedItem, String> sizeResolver,
            LiveRecommendationCompleteness completeness, List<ProductCategory> found, List<ProductCategory> missing,
            List<RequestedItemSummary> foundItems, List<RequestedItemSummary> missingItems, String message, Instant now) {
        String name = "Live Look " + rank;
        String explanation = buildExplanation(outfit);
        LiveOutfitRecommendation recommendation = LiveOutfitRecommendation.active(
                eventId, generation, rank, name, explanation, completeness, found, missing, foundItems, missingItems, message, now);

        int displayOrder = 0;
        for (LiveSelectedItem selected : outfit.items()) {
            RetailProductCandidate candidate = selected.candidate();
            String requestedSize = sizeResolver.apply(selected);
            if (selected.requestedItem() != null) {
                RequestedItem requestedItem = selected.requestedItem();
                log.debug("Persisting recommendation item: productUrl={}, persistedImageUrlPresent={}",
                        candidate.productUrl(), candidate.imageUrl() != null);
                recommendation.addItem(new LiveOutfitItem(
                        UUID.randomUUID(), requestedItem.id(), requestedItem.originalPhrase(), requestedItem.genericCategory(),
                        candidate.retailer(), candidate.title(), candidate.brand(), candidate.productUrl(), candidate.imageUrl(),
                        candidate.price(), candidate.originalPrice(), candidate.currency(), candidate.priceVerified(),
                        candidate.color(), requestedSize, candidate.availableSizes(), candidate.sizeVerified(),
                        candidate.stockText(), candidate.availabilityVerified(), candidate.audience(), candidate.sourceCitation(),
                        displayOrder++, now));
            } else {
                log.debug("Persisting recommendation item: productUrl={}, persistedImageUrlPresent={}",
                        candidate.productUrl(), candidate.imageUrl() != null);
                recommendation.addItem(new LiveOutfitItem(
                        UUID.randomUUID(), selected.category(), candidate.retailer(), candidate.title(), candidate.brand(),
                        candidate.productUrl(), candidate.imageUrl(), candidate.price(), candidate.originalPrice(),
                        candidate.currency(), candidate.priceVerified(), candidate.color(), requestedSize,
                        candidate.availableSizes(), candidate.sizeVerified(), candidate.stockText(),
                        candidate.availabilityVerified(), candidate.audience(), candidate.sourceCitation(),
                        displayOrder++, now));
            }
        }
        repository.save(recommendation);
    }

    private void supersedeActiveRecommendations(UUID eventId, Instant now) {
        List<LiveOutfitRecommendation> active = repository.findByEventIdAndStatus(eventId, RecommendationStatus.ACTIVE);
        active.forEach(recommendation -> recommendation.supersede(now));
        repository.saveAll(active);
    }

    private String buildCategoryMessage(LiveRecommendationCompleteness completeness, List<ProductCategory> found, List<ProductCategory> missing) {
        return switch (completeness) {
            case COMPLETE -> null;
            case PARTIAL -> "We found items for " + formatCategoryList(found) + ", but no matching "
                    + formatCategoryList(missing) + ".";
            case NO_RESULTS -> "No live Nordstrom products were found for required categor"
                    + (missing.size() == 1 ? "y: " : "ies: ") + formatCategoryList(missing) + ".";
            case PROVIDER_UNAVAILABLE -> "Live Nordstrom search is temporarily unavailable. Please try again shortly.";
        };
    }

    private String buildItemMessage(LiveRecommendationCompleteness completeness, List<RequestedItem> found, List<RequestedItem> missing) {
        return switch (completeness) {
            case COMPLETE -> null;
            case PARTIAL -> "We found " + formatItemPhraseList(found) + ", but couldn't find a matching Nordstrom "
                    + "product for " + formatItemPhraseList(missing) + ".";
            case NO_RESULTS -> "No live Nordstrom products were found for requested item"
                    + (missing.size() == 1 ? ": " : "s: ") + formatItemPhraseList(missing) + ".";
            case PROVIDER_UNAVAILABLE -> "Live Nordstrom search is temporarily unavailable. Please try again shortly.";
        };
    }

    private String formatCategoryList(List<ProductCategory> categories) {
        return joinLabels(categories.stream().map(this::formatCategoryLabel).toList());
    }

    private String formatItemPhraseList(List<RequestedItem> items) {
        return joinLabels(items.stream().map(RequestedItem::originalPhrase).toList());
    }

    private String joinLabels(List<String> labels) {
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
        String labels = outfit.items().stream()
                .map(item -> item.requestedItem() != null ? item.requestedItem().originalPhrase() : item.category().name())
                .collect(Collectors.joining(", "));
        return "Includes " + outfit.items().size() + " live nordstrom.com product(s) (" + labels + ").";
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
                summary.getFoundCategories(), summary.getMissingCategories(),
                summary.getFoundRequestedItems(), summary.getMissingRequestedItems(),
                summary.getMessage(), recommendations, summary.isStale());
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
        log.debug("Mapping recommendation item to API DTO: productUrl={}, apiDtoImageUrlPresent={}",
                item.getProductUrl(), item.getImageUrl() != null);
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
                item.getRequestedItemPhrase(),
                item.getRequestedItemGenericCategory(),
                item.getSourceCitation(),
                item.getDisplayOrder());
    }
}

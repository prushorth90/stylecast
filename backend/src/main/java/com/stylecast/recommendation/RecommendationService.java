package com.stylecast.recommendation;

import com.stylecast.catalog.Product;
import com.stylecast.catalog.ProductCategory;
import com.stylecast.catalog.ProductRepository;
import com.stylecast.catalog.ProductVariant;
import com.stylecast.catalog.ProductVariantRepository;
import com.stylecast.recommendation.dto.OutfitItemResponse;
import com.stylecast.recommendation.dto.OutfitRecommendationResponse;
import com.stylecast.recommendation.dto.RecommendationsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import java.time.Instant;

/**
 * Application service orchestrating deterministic outfit generation for an
 * event, entirely from the local product catalog:
 *
 * <pre>
 * RecommendationContextLoader -> OutfitTemplateSelector -> ProductEligibilityService
 *   -> OutfitCombinationGenerator (per template) -> HardConstraintValidator
 *   -> OutfitScorer -> select up to 3 distinct outfits -> persist -> respond
 * </pre>
 *
 * <p>Never calls a live retail provider, OpenAI, or any other network/LLM
 * API - every candidate item comes from {@code com.stylecast.catalog}
 * repositories reading the local database.
 *
 * <p>Regeneration is versioned: {@link #generate} always creates a new
 * {@code generation} number, marks the previous generation's {@code ACTIVE}
 * rows {@link RecommendationStatus#SUPERSEDED}, and never deletes history.
 * {@link #getCurrent} only ever reads the latest generation and never
 * triggers generation itself - repeating {@code GET} is always safe.
 */
@Service
public class RecommendationService {

    private static final int MAX_OUTFITS = 3;
    private static final int DIVERSITY_OVERLAP_PENALTY = 3;

    private final RecommendationContextLoader contextLoader;
    private final OutfitTemplateSelector templateSelector;
    private final ProductEligibilityService eligibilityService;
    private final OutfitCombinationGenerator combinationGenerator;
    private final OutfitScorer scorer;
    private final OutfitRecommendationRepository repository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;

    public RecommendationService(
            RecommendationContextLoader contextLoader,
            OutfitTemplateSelector templateSelector,
            ProductEligibilityService eligibilityService,
            OutfitCombinationGenerator combinationGenerator,
            OutfitScorer scorer,
            OutfitRecommendationRepository repository,
            ProductRepository productRepository,
            ProductVariantRepository productVariantRepository) {
        this.contextLoader = contextLoader;
        this.templateSelector = templateSelector;
        this.eligibilityService = eligibilityService;
        this.combinationGenerator = combinationGenerator;
        this.scorer = scorer;
        this.repository = repository;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
    }

    /**
     * Generates (or regenerates) up to three ranked outfit recommendations
     * for an event from the local catalog and persists them as a new
     * generation. Throws {@link com.stylecast.event.EventNotFoundException}
     * (404), {@link MissingStylePreferencesException} (409), or {@link
     * MissingOccasionInterpretationException} (409) if a prerequisite is
     * missing. Finding zero valid outfits is not an error - it persists a
     * {@link RecommendationStatus#NO_VALID_OUTFIT} row and returns normally.
     */
    @Transactional
    public RecommendationsResponse generate(UUID eventId) {
        RecommendationContext context = contextLoader.load(eventId);

        List<OutfitTemplate> templates = templateSelector.selectTemplates(context);
        Map<ProductCategory, List<EligibleCandidate>> eligible = eligibilityService.findEligible(context);

        List<ScoredCandidate> scoredCandidates = new ArrayList<>();
        for (OutfitTemplate template : templates) {
            for (OutfitCandidate candidate : combinationGenerator.generate(template, eligible, context)) {
                scoredCandidates.add(new ScoredCandidate(candidate, scorer.score(candidate, context)));
            }
        }

        List<ScoredCandidate> selected = selectTopDistinct(scoredCandidates);

        Instant now = Instant.now();
        int nextGeneration = repository.findFirstByEventIdOrderByGenerationDesc(eventId)
                .map(r -> r.getGeneration() + 1)
                .orElse(1);
        supersedeActiveRecommendations(eventId, now);

        if (selected.isEmpty()) {
            String reason = buildNoResultReason(context, eligible);
            repository.save(OutfitRecommendation.noValidOutfit(eventId, nextGeneration, reason, now));
        } else {
            int rank = 1;
            for (ScoredCandidate scored : selected) {
                persistOutfit(eventId, nextGeneration, rank, scored, context, now);
                rank++;
            }
        }

        return buildResponse(eventId);
    }

    /**
     * Returns the event's current (latest generation) recommendations
     * without generating anything - repeated calls never re-run generation.
     * Returns a {@code hasResults=false} response (not an error) when
     * nothing has ever been generated for this event.
     */
    @Transactional(readOnly = true)
    public RecommendationsResponse getCurrent(UUID eventId) {
        contextLoader.requireEvent(eventId);
        return buildResponse(eventId);
    }

    private void persistOutfit(UUID eventId, int generation, int rank, ScoredCandidate scored, RecommendationContext context, Instant now) {
        String name = buildOutfitName(scored.candidate(), rank);
        String explanation = buildExplanation(scored.candidate(), context, scored.score());
        OutfitRecommendation recommendation = OutfitRecommendation.active(
                eventId, generation, rank, name, explanation, scored.score(), scored.candidate().totalPrice(), now);

        int displayOrder = 0;
        for (SelectedItem item : scored.candidate().items()) {
            recommendation.addItem(new OutfitItem(
                    UUID.randomUUID(),
                    item.candidate().product().getId(),
                    item.candidate().variant().getId(),
                    item.category(),
                    item.candidate().effectivePrice(),
                    displayOrder++,
                    now));
        }
        repository.save(recommendation);
    }

    private void supersedeActiveRecommendations(UUID eventId, Instant now) {
        List<OutfitRecommendation> active = repository.findByEventIdAndStatus(eventId, RecommendationStatus.ACTIVE);
        active.forEach(recommendation -> recommendation.supersede(now));
        repository.saveAll(active);
    }

    /**
     * Selects up to {@link #MAX_OUTFITS} outfits: highest {@code
     * overallScore} first, with identical item-sets de-duplicated, and a
     * documented diversity nudge - at each pick, candidates that share more
     * products with outfits already selected are penalized by {@link
     * #DIVERSITY_OVERLAP_PENALTY} points per shared product before the next
     * pick is chosen, so a near-tied but more different outfit is preferred
     * over a near-duplicate of one already picked. All comparisons are
     * fully deterministic (ties broken by price, then a canonical sorted
     * product-id key) - the same input always produces the same output.
     */
    private List<ScoredCandidate> selectTopDistinct(List<ScoredCandidate> scoredCandidates) {
        Map<String, ScoredCandidate> distinctByItemSet = new java.util.LinkedHashMap<>();
        for (ScoredCandidate candidate : scoredCandidates) {
            distinctByItemSet.merge(productIdKey(candidate.candidate()), candidate,
                    (existing, incoming) -> compareForSelection(existing, incoming) >= 0 ? existing : incoming);
        }

        List<ScoredCandidate> remaining = new ArrayList<>(distinctByItemSet.values());
        List<ScoredCandidate> selected = new ArrayList<>();

        while (!remaining.isEmpty() && selected.size() < MAX_OUTFITS) {
            ScoredCandidate best = remaining.stream()
                    .max(Comparator.comparingInt((ScoredCandidate c) -> adjustedScore(c, selected))
                            .thenComparingInt(c -> c.score().overallScore())
                            .thenComparing((ScoredCandidate c) -> c.candidate().totalPrice(), Comparator.reverseOrder())
                            .thenComparing(c -> productIdKey(c.candidate())))
                    .orElseThrow();
            selected.add(best);
            remaining.remove(best);
        }
        return selected;
    }

    private int adjustedScore(ScoredCandidate candidate, List<ScoredCandidate> alreadySelected) {
        java.util.Set<UUID> selectedProductIds = alreadySelected.stream()
                .flatMap(sc -> sc.candidate().items().stream())
                .map(item -> item.candidate().product().getId())
                .collect(Collectors.toSet());
        long overlap = candidate.candidate().items().stream()
                .map(item -> item.candidate().product().getId())
                .filter(selectedProductIds::contains)
                .count();
        return candidate.score().overallScore() - (int) overlap * DIVERSITY_OVERLAP_PENALTY;
    }

    private int compareForSelection(ScoredCandidate a, ScoredCandidate b) {
        return Integer.compare(a.score().overallScore(), b.score().overallScore());
    }

    private String productIdKey(OutfitCandidate candidate) {
        return candidate.items().stream()
                .map(item -> item.candidate().product().getId().toString())
                .sorted()
                .collect(Collectors.joining(","));
    }

    private String buildNoResultReason(RecommendationContext context, Map<ProductCategory, List<EligibleCandidate>> eligible) {
        List<ProductCategory> emptyRequired = context.requiredCategories().stream()
                .filter(category -> eligible.getOrDefault(category, List.of()).isEmpty())
                .toList();

        if (!emptyRequired.isEmpty()) {
            String categories = emptyRequired.stream().map(Enum::name).collect(Collectors.joining(", "));
            return "No active, in-stock products matching the requested size and color constraints were found "
                    + "for required categor" + (emptyRequired.size() == 1 ? "y: " : "ies: ") + categories + ".";
        }

        return "No combination of eligible products satisfies the budget of " + context.maxBudget()
                + " together with all size, color, formality, and weather requirements.";
    }

    private String buildOutfitName(OutfitCandidate candidate, int rank) {
        return formatTemplateLabel(candidate.templateName()) + " - Look " + rank;
    }

    private String formatTemplateLabel(String templateName) {
        return java.util.Arrays.stream(templateName.split("_"))
                .map(word -> word.substring(0, 1) + word.substring(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
    }

    private String buildExplanation(OutfitCandidate candidate, RecommendationContext context, OutfitScore score) {
        String categories = candidate.items().stream().map(item -> item.category().name()).collect(Collectors.joining(", "));
        return "Includes " + candidate.items().size() + " items (" + categories + ") for " + context.occasion()
                + " at formality " + context.formalityLevel() + "/10; total $" + candidate.totalPrice()
                + " within your $" + context.maxBudget() + " budget; overall fit score " + score.overallScore() + "/100.";
    }

    private RecommendationsResponse buildResponse(UUID eventId) {
        Optional<OutfitRecommendation> latest = repository.findFirstByEventIdOrderByGenerationDesc(eventId);
        if (latest.isEmpty()) {
            return RecommendationsResponse.notGeneratedYet(eventId);
        }

        int generation = latest.get().getGeneration();
        List<OutfitRecommendation> rows = repository
                .findByEventIdAndStatusInOrderByRankPositionAsc(eventId, List.of(RecommendationStatus.ACTIVE, RecommendationStatus.NO_VALID_OUTFIT))
                .stream()
                .filter(row -> row.getGeneration() == generation)
                .toList();

        boolean hasResults = rows.stream().anyMatch(row -> row.getStatus() == RecommendationStatus.ACTIVE);
        String noResultReason = hasResults ? null : rows.stream()
                .findFirst()
                .map(OutfitRecommendation::getNoResultReason)
                .orElse(null);

        List<OutfitRecommendationResponse> recommendations = rows.stream()
                .filter(row -> row.getStatus() == RecommendationStatus.ACTIVE)
                .map(this::toResponse)
                .toList();

        Instant generatedAt = rows.isEmpty() ? latest.get().getGeneratedAt() : rows.get(0).getGeneratedAt();

        return new RecommendationsResponse(eventId, generation, generatedAt, hasResults, noResultReason, recommendations);
    }

    private OutfitRecommendationResponse toResponse(OutfitRecommendation recommendation) {
        List<OutfitItemResponse> items = recommendation.getItems().stream().map(this::toResponse).toList();
        return new OutfitRecommendationResponse(
                recommendation.getId(),
                recommendation.getEventId(),
                recommendation.getGeneration(),
                recommendation.getRankPosition(),
                recommendation.getName(),
                recommendation.getStatus(),
                recommendation.getSource(),
                recommendation.getTotalPrice(),
                recommendation.getOccasionFitScore(),
                recommendation.getWeatherFitScore(),
                recommendation.getStyleFitScore(),
                recommendation.getColorFitScore(),
                recommendation.getBudgetEfficiencyScore(),
                recommendation.getCompletenessScore(),
                recommendation.getOverallScore(),
                recommendation.getExplanation(),
                recommendation.getGeneratedAt(),
                items);
    }

    private OutfitItemResponse toResponse(OutfitItem item) {
        Product product = productRepository.findById(item.getProductId())
                .orElseThrow(() -> new IllegalStateException("Referenced product no longer exists: " + item.getProductId()));
        ProductVariant variant = productVariantRepository.findById(item.getProductVariantId())
                .orElseThrow(() -> new IllegalStateException("Referenced product variant no longer exists: " + item.getProductVariantId()));

        return new OutfitItemResponse(
                item.getId(),
                product.getId(),
                variant.getId(),
                item.getCategory(),
                product.getBrand(),
                product.getName(),
                variant.getColor(),
                variant.getClothingSize(),
                item.getItemPrice(),
                item.getDisplayOrder(),
                product.getImageUrl());
    }

    private record ScoredCandidate(OutfitCandidate candidate, OutfitScore score) {
    }
}

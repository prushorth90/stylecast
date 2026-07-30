package com.stylecast.recommendation;

import com.stylecast.catalog.ProductCategory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Deterministically assembles complete, hard-constraint-satisfying outfits
 * from one {@link OutfitTemplate} and the per-category candidates found by
 * {@link ProductEligibilityService}.
 *
 * <p>Bounded backtracking, not a full cross-product: each required slot
 * only branches over its top {@link #MAX_CANDIDATES_PER_SLOT} candidates
 * (pre-sorted by formality closeness, then price, then product id - see
 * {@link ProductEligibilityService}), each optional slot branches over
 * "include its single best candidate" or "skip it", and the search stops
 * once {@link #MAX_COMBINATIONS_PER_TEMPLATE} valid combinations have been
 * found. This keeps the search small and fast for this catalog's size while
 * still exploring enough alternatives to produce up to three genuinely
 * distinct outfits. Every produced combination is re-validated as a whole
 * by {@link HardConstraintValidator#validateOutfit} before being returned.
 */
@Component
class OutfitCombinationGenerator {

    private static final int MAX_CANDIDATES_PER_SLOT = 3;
    private static final int MAX_COMBINATIONS_PER_TEMPLATE = 40;

    private final HardConstraintValidator hardConstraintValidator;

    OutfitCombinationGenerator(HardConstraintValidator hardConstraintValidator) {
        this.hardConstraintValidator = hardConstraintValidator;
    }

    List<OutfitCandidate> generate(
            OutfitTemplate template, Map<ProductCategory, List<EligibleCandidate>> eligibleByCategory, RecommendationContext context) {

        if (!everyUnconditionalRequiredSlotHasCandidates(template, eligibleByCategory)) {
            return List.of();
        }

        List<Map<String, SelectedItem>> assemblies = new ArrayList<>();
        recurse(template.slots(), 0, new LinkedHashMap<>(), BigDecimal.ZERO, assemblies, eligibleByCategory, context);

        List<OutfitCandidate> validCandidates = new ArrayList<>();
        for (Map<String, SelectedItem> assembly : assemblies) {
            OutfitCandidate candidate = toOutfitCandidate(template, assembly);
            if (hardConstraintValidator.validateOutfit(candidate, context).isEmpty()) {
                validCandidates.add(candidate);
            }
        }
        return validCandidates;
    }

    private boolean everyUnconditionalRequiredSlotHasCandidates(
            OutfitTemplate template, Map<ProductCategory, List<EligibleCandidate>> eligibleByCategory) {
        for (TemplateSlot slot : template.slots()) {
            if (slot.required() && !slot.isConditional() && candidatesFor(slot, eligibleByCategory).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void recurse(
            List<TemplateSlot> slots,
            int index,
            Map<String, SelectedItem> resolved,
            BigDecimal runningTotal,
            List<Map<String, SelectedItem>> results,
            Map<ProductCategory, List<EligibleCandidate>> eligibleByCategory,
            RecommendationContext context) {

        if (results.size() >= MAX_COMBINATIONS_PER_TEMPLATE) {
            return;
        }
        if (index == slots.size()) {
            results.add(new LinkedHashMap<>(resolved));
            return;
        }

        TemplateSlot slot = slots.get(index);

        if (slot.isConditional()) {
            SelectedItem dependency = resolved.get(slot.dependsOnSlot());
            boolean triggered = dependency != null && slot.requiredWhenDependencyIn().contains(dependency.category());
            if (!triggered) {
                recurse(slots, index + 1, resolved, runningTotal, results, eligibleByCategory, context);
                return;
            }
        }

        boolean effectivelyRequired = slot.required() || slot.isConditional();
        List<SelectedItem> candidates = candidatesFor(slot, eligibleByCategory).stream()
                .filter(item -> !usesAlreadySelectedProduct(item, resolved))
                .filter(item -> runningTotal.add(item.candidate().effectivePrice()).compareTo(context.maxBudget()) <= 0)
                .limit(MAX_CANDIDATES_PER_SLOT)
                .toList();

        if (!effectivelyRequired) {
            // Optional slot: try skipping it first (favors leaner, cheaper outfits
            // among otherwise-equal branches), then try including its top candidates.
            recurse(slots, index + 1, resolved, runningTotal, results, eligibleByCategory, context);
        }

        for (SelectedItem candidate : candidates) {
            resolved.put(slot.name(), candidate);
            recurse(slots, index + 1, resolved, runningTotal.add(candidate.candidate().effectivePrice()),
                    results, eligibleByCategory, context);
            resolved.remove(slot.name());
            if (results.size() >= MAX_COMBINATIONS_PER_TEMPLATE) {
                return;
            }
        }
    }

    private boolean usesAlreadySelectedProduct(SelectedItem item, Map<String, SelectedItem> resolved) {
        return resolved.values().stream()
                .anyMatch(selected -> selected.candidate().product().getId().equals(item.candidate().product().getId()));
    }

    private List<SelectedItem> candidatesFor(TemplateSlot slot, Map<ProductCategory, List<EligibleCandidate>> eligibleByCategory) {
        return slot.alternatives().stream()
                .flatMap(category -> eligibleByCategory.getOrDefault(category, List.of()).stream()
                        .map(candidate -> new SelectedItem(category, candidate)))
                .sorted(Comparator.comparing(item -> item.candidate().effectivePrice()))
                .collect(Collectors.toList());
    }

    private OutfitCandidate toOutfitCandidate(OutfitTemplate template, Map<String, SelectedItem> assembly) {
        List<SelectedItem> items = template.slots().stream()
                .map(slot -> assembly.get(slot.name()))
                .filter(java.util.Objects::nonNull)
                .toList();
        return new OutfitCandidate(template.name(), items);
    }
}

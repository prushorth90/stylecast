package com.stylecast.recommendation;

import com.stylecast.catalog.ProductCategory;
import com.stylecast.occasion.OccasionType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Chooses which {@link OutfitTemplate}s are worth attempting for an event,
 * based only on the occasion interpretation already produced by {@code
 * com.stylecast.occasion} (never re-deriving occasion/formality itself).
 *
 * <p>Selection is deterministic and additive: a dress-based template is
 * tried whenever the interpretation's categories suggest one (so the engine
 * never assumes every event needs menswear), and exactly one
 * formality/occasion-appropriate menswear-style template is always tried
 * alongside it. Returning more than one template is what lets {@link
 * OutfitCombinationGenerator} produce outfits with genuinely different
 * silhouettes for the same event, rather than only varying individual
 * items within a single fixed shape.
 */
@Component
class OutfitTemplateSelector {

    private static final Set<OccasionType> BUSINESS_OCCASIONS = Set.of(
            OccasionType.INTERVIEW, OccasionType.BUSINESS_MEETING, OccasionType.NETWORKING, OccasionType.CONFERENCE);

    private static final Set<OccasionType> FORMAL_OCCASIONS = Set.of(
            OccasionType.WEDDING, OccasionType.FORMAL_EVENT);

    List<OutfitTemplate> selectTemplates(RecommendationContext context) {
        Set<OutfitTemplate> templates = new LinkedHashSet<>();

        if (suggestsDressBased(context)) {
            templates.add(OutfitTemplateCatalog.DRESS_BASED);
        }

        templates.add(selectMenswearStyleTemplate(context));

        return List.copyOf(templates);
    }

    private boolean suggestsDressBased(RecommendationContext context) {
        Set<ProductCategory> signaled = new LinkedHashSet<>(context.requiredCategories());
        signaled.addAll(context.optionalCategories());
        return signaled.contains(ProductCategory.DRESS) || signaled.contains(ProductCategory.SKIRT);
    }

    private OutfitTemplate selectMenswearStyleTemplate(RecommendationContext context) {
        OccasionType occasion = context.occasion();
        int formality = context.formalityLevel();

        if (formality >= 8 || FORMAL_OCCASIONS.contains(occasion)) {
            return OutfitTemplateCatalog.FORMAL_MENSWEAR;
        }
        if (formality >= 6 || BUSINESS_OCCASIONS.contains(occasion)) {
            return OutfitTemplateCatalog.BUSINESS_INTERVIEW;
        }
        return OutfitTemplateCatalog.SMART_CASUAL;
    }
}

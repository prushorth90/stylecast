package com.stylecast.recommendation;

import com.stylecast.catalog.ProductCategory;
import com.stylecast.event.Event;
import com.stylecast.event.styling.EventStylePreferences;
import com.stylecast.event.styling.PreferredStyle;
import com.stylecast.event.styling.ShoppingDepartment;
import com.stylecast.occasion.OccasionInterpretation;
import com.stylecast.occasion.OccasionType;
import com.stylecast.retail.RetailProductSearchRequest;
import com.stylecast.retail.Retailer;
import com.stylecast.retail.TargetAudience;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LiveCategorySearchRequestFactoryTest {

    private final LiveCategorySearchRequestFactory factory = new LiveCategorySearchRequestFactory();

    private RecommendationContext buildContext(BigDecimal maxBudget, List<ProductCategory> required) {
        return buildContext(maxBudget, required, ShoppingDepartment.NO_PREFERENCE);
    }

    private RecommendationContext buildContext(BigDecimal maxBudget, List<ProductCategory> required, ShoppingDepartment shoppingDepartment) {
        Event event = RecommendationFixtures.event();
        EventStylePreferences preferences = RecommendationFixtures.preferences(
                event.getId(), maxBudget, "M", "9", PreferredStyle.CLASSIC, List.of("navy"), List.of(), shoppingDepartment);
        OccasionInterpretation interpretation = RecommendationFixtures.interpretation(
                event.getId(), OccasionType.WEDDING, 9, required, List.of(), List.of());
        return RecommendationFixtures.context(event, preferences, interpretation, Optional.empty());
    }

    @Test
    void buildRequests_createsExactlyOneRequestPerRequiredCategory() {
        RecommendationContext context = buildContext(BigDecimal.valueOf(900), List.of(ProductCategory.SUIT, ProductCategory.SHOES));

        List<RetailProductSearchRequest> requests =
                factory.buildRequests(context, context.requiredCategories());

        assertThat(requests).hasSize(2);
        assertThat(requests).allSatisfy(r -> assertThat(r.retailer()).isEqualTo(Retailer.NORDSTROM));
        assertThat(requests.stream().map(RetailProductSearchRequest::category))
                .containsExactlyInAnyOrder(ProductCategory.SUIT, ProductCategory.SHOES);
    }

    @Test
    void buildRequests_splitsBudgetEvenlyAcrossCategories() {
        RecommendationContext context = buildContext(BigDecimal.valueOf(900), List.of(ProductCategory.SUIT, ProductCategory.SHOES));

        List<RetailProductSearchRequest> requests = factory.buildRequests(context, context.requiredCategories());

        assertThat(requests).allSatisfy(r -> assertThat(r.maxPrice()).isEqualByComparingTo("450.00"));
    }

    @Test
    void buildRequests_usesShoeSizeForShoesAndClothingSizeForApparel() {
        RecommendationContext context = buildContext(BigDecimal.valueOf(900), List.of(ProductCategory.SUIT, ProductCategory.SHOES));

        List<RetailProductSearchRequest> requests = factory.buildRequests(context, context.requiredCategories());

        RetailProductSearchRequest suitRequest = requests.stream().filter(r -> r.category() == ProductCategory.SUIT).findFirst().orElseThrow();
        RetailProductSearchRequest shoesRequest = requests.stream().filter(r -> r.category() == ProductCategory.SHOES).findFirst().orElseThrow();

        assertThat(suitRequest.clothingSize()).isEqualTo("M");
        assertThat(shoesRequest.clothingSize()).isEqualTo("9");
    }

    @Test
    void buildRequests_usesNoSizeHintForAccessories() {
        RecommendationContext context = buildContext(BigDecimal.valueOf(900),
                List.of(ProductCategory.SUIT, ProductCategory.SHOES, ProductCategory.ACCESSORY));

        List<RetailProductSearchRequest> requests = factory.buildRequests(context, context.requiredCategories());

        RetailProductSearchRequest accessoryRequest =
                requests.stream().filter(r -> r.category() == ProductCategory.ACCESSORY).findFirst().orElseThrow();

        assertThat(accessoryRequest.clothingSize()).isNull();
    }

    @Test
    void buildRequests_keywordsAreDerivedAutomaticallyFromContext() {
        RecommendationContext context = buildContext(BigDecimal.valueOf(900), List.of(ProductCategory.SUIT));

        List<RetailProductSearchRequest> requests = factory.buildRequests(context, context.requiredCategories());

        assertThat(requests.get(0).keywords()).contains("navy", "classic", "wedding");
    }

    // --- Target audience / department derivation (preference-driven, candidate-filtering fix) ---

    @Test
    void buildRequests_forMenDepartment_setsMenTargetAudienceOnEveryCategoryAndAddsMenKeywords() {
        // The exact reported bug scenario: a men's wedding outfit still needs SHIRT/SHOES
        // searched without drifting into women's departments.
        RecommendationContext context = buildContext(BigDecimal.valueOf(2000),
                List.of(ProductCategory.SUIT, ProductCategory.SHIRT, ProductCategory.SHOES), ShoppingDepartment.MEN);

        List<RetailProductSearchRequest> requests = factory.buildRequests(context, context.requiredCategories());

        assertThat(requests).allSatisfy(r -> assertThat(r.targetAudience()).isEqualTo(TargetAudience.MEN));
        assertThat(requests).allSatisfy(r -> assertThat(r.keywords()).contains("men's", "mens"));
        assertThat(requests).noneSatisfy(r -> assertThat(r.keywords()).containsAnyOf("women's", "womens"));
    }

    @Test
    void buildRequests_forWomenDepartment_setsWomenTargetAudienceAndAddsWomenKeywords() {
        RecommendationContext context = buildContext(BigDecimal.valueOf(2000),
                List.of(ProductCategory.DRESS, ProductCategory.SHOES), ShoppingDepartment.WOMEN);

        List<RetailProductSearchRequest> requests = factory.buildRequests(context, context.requiredCategories());

        assertThat(requests).allSatisfy(r -> assertThat(r.targetAudience()).isEqualTo(TargetAudience.WOMEN));
        assertThat(requests).allSatisfy(r -> assertThat(r.keywords()).contains("women's", "womens"));
    }

    @Test
    void buildRequests_forUnisexDepartment_setsUnisexTargetAudienceAndAddsUnisexKeywords() {
        RecommendationContext context = buildContext(BigDecimal.valueOf(900),
                List.of(ProductCategory.SHOES, ProductCategory.ACCESSORY), ShoppingDepartment.UNISEX);

        List<RetailProductSearchRequest> requests = factory.buildRequests(context, context.requiredCategories());

        assertThat(requests).allSatisfy(r -> assertThat(r.targetAudience()).isEqualTo(TargetAudience.UNISEX));
        assertThat(requests).allSatisfy(r -> assertThat(r.keywords()).contains("unisex", "gender-neutral"));
    }

    @Test
    void buildRequests_forNoPreferenceDepartment_setsNoPreferenceTargetAudienceAndAddsNoDepartmentKeywords() {
        RecommendationContext context = buildContext(BigDecimal.valueOf(900),
                List.of(ProductCategory.SUIT, ProductCategory.DRESS), ShoppingDepartment.NO_PREFERENCE);

        List<RetailProductSearchRequest> requests = factory.buildRequests(context, context.requiredCategories());

        assertThat(requests).allSatisfy(r -> assertThat(r.targetAudience()).isEqualTo(TargetAudience.NO_PREFERENCE));
        assertThat(requests).allSatisfy(r -> assertThat(r.keywords())
                .doesNotContain("men's", "mens", "women's", "womens", "unisex", "gender-neutral"));
    }

    // --- Deterministic category synonyms (search recall, no extra API calls) ---

    @Test
    void buildRequests_includesDeterministicSynonymsForTrousers() {
        RecommendationContext context = buildContext(BigDecimal.valueOf(900), List.of(ProductCategory.TROUSERS));

        List<RetailProductSearchRequest> requests = factory.buildRequests(context, context.requiredCategories());

        RetailProductSearchRequest trousersRequest =
                requests.stream().filter(r -> r.category() == ProductCategory.TROUSERS).findFirst().orElseThrow();
        assertThat(trousersRequest.keywords()).contains("trousers", "dress pants", "pants", "chinos");
    }

    @Test
    void buildRequests_includesDeterministicSynonymsForEveryListedCategory() {
        RecommendationContext context = buildContext(BigDecimal.valueOf(2000),
                List.of(ProductCategory.SUIT, ProductCategory.TROUSERS, ProductCategory.SHIRT, ProductCategory.ACCESSORY));

        List<RetailProductSearchRequest> requests = factory.buildRequests(context, context.requiredCategories());

        assertKeywordsContain(requests, ProductCategory.SUIT, "suit", "tuxedo", "dinner jacket");
        assertKeywordsContain(requests, ProductCategory.SHIRT, "shirt", "dress shirt", "button-up shirt");
        assertKeywordsContain(requests, ProductCategory.SHOES, "dress shoes", "loafers", "oxfords");
        assertKeywordsContain(requests, ProductCategory.ACCESSORY, "tie", "belt", "pocket square");
    }

    @Test
    void buildRequests_categoryWithNoDefinedSynonyms_addsNoSynonymKeywords() {
        RecommendationContext context = buildContext(BigDecimal.valueOf(2000),
                List.of(ProductCategory.DRESS, ProductCategory.SHOES));

        List<RetailProductSearchRequest> requests = factory.buildRequests(context, context.requiredCategories());

        RetailProductSearchRequest dressRequest =
                requests.stream().filter(r -> r.category() == ProductCategory.DRESS).findFirst().orElseThrow();
        assertThat(dressRequest.keywords()).doesNotContain("suit", "tuxedo", "trousers", "shirt");
    }

    private void assertKeywordsContain(List<RetailProductSearchRequest> requests, ProductCategory category, String... expected) {
        RetailProductSearchRequest request = requests.stream().filter(r -> r.category() == category).findFirst().orElseThrow();
        assertThat(request.keywords()).contains(expected);
    }
}

package com.stylecast.recommendation;

import com.stylecast.event.Event;
import com.stylecast.event.styling.EventStylePreferences;
import com.stylecast.event.styling.PreferredStyle;
import com.stylecast.event.styling.ShoppingDepartment;
import com.stylecast.occasion.GenericItemCategory;
import com.stylecast.occasion.OccasionInterpretation;
import com.stylecast.occasion.OccasionType;
import com.stylecast.occasion.RequestedItem;
import com.stylecast.recommendation.RequestedItemSearchRequestFactory.RequestedItemSearchRequest;
import com.stylecast.retail.RetailProductSearchRequest;
import com.stylecast.retail.Retailer;
import com.stylecast.retail.TargetAudience;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RequestedItemSearchRequestFactoryTest {

    private final RequestedItemSearchRequestFactory factory = new RequestedItemSearchRequestFactory();

    private RequestedItem item(String phrase, GenericItemCategory category, List<String> searchTerms, String activityContext) {
        return new RequestedItem(UUID.randomUUID(), phrase, category, searchTerms, true, activityContext, 0);
    }

    private RecommendationContext buildContext(BigDecimal maxBudget, ShoppingDepartment shoppingDepartment) {
        Event event = RecommendationFixtures.event();
        EventStylePreferences preferences = RecommendationFixtures.preferences(
                event.getId(), maxBudget, "M", "9", PreferredStyle.CLASSIC, List.of("navy"), List.of(), shoppingDepartment);
        OccasionInterpretation interpretation = RecommendationFixtures.interpretation(
                event.getId(), OccasionType.CASUAL_OUTING, 2, List.of(), List.of(), List.of());
        return RecommendationFixtures.context(event, preferences, interpretation, Optional.empty());
    }

    @Test
    void buildRequests_createsExactlyOneRequestPerItem() {
        RecommendationContext context = buildContext(BigDecimal.valueOf(300), ShoppingDepartment.NO_PREFERENCE);
        RequestedItem jersey = item("USA soccer jersey", GenericItemCategory.TOP, List.of("USA soccer jersey"), "soccer");
        RequestedItem boots = item("football boots", GenericItemCategory.FOOTWEAR, List.of("football boots", "soccer cleats"), "soccer");

        List<RequestedItemSearchRequest> requests = factory.buildRequests(context, List.of(jersey, boots));

        assertThat(requests).hasSize(2);
        assertThat(requests).allSatisfy(r -> assertThat(r.request().retailer()).isEqualTo(Retailer.NORDSTROM));
    }

    @Test
    void buildRequests_neverSetsACatalogCategoryHint() {
        RecommendationContext context = buildContext(BigDecimal.valueOf(300), ShoppingDepartment.NO_PREFERENCE);
        RequestedItem jersey = item("USA soccer jersey", GenericItemCategory.TOP, List.of("USA soccer jersey"), "soccer");

        List<RequestedItemSearchRequest> requests = factory.buildRequests(context, List.of(jersey));

        // The whole point of the explicit-item pipeline: never fall back to a broad
        // catalog category name - only the item's own phrase/search terms are used.
        assertThat(requests.get(0).request().category()).isNull();
    }

    @Test
    void buildRequests_keywordsIncludeOriginalPhraseSearchTermsAndActivityContext() {
        RecommendationContext context = buildContext(BigDecimal.valueOf(300), ShoppingDepartment.NO_PREFERENCE);
        RequestedItem boots = item("football boots", GenericItemCategory.FOOTWEAR, List.of("football boots", "soccer cleats"), "soccer");

        List<RequestedItemSearchRequest> requests = factory.buildRequests(context, List.of(boots));

        RetailProductSearchRequest request = requests.get(0).request();
        assertThat(request.keywords()).contains("football boots", "soccer cleats", "soccer");
    }

    @Test
    void buildRequests_splitsBudgetEvenlyAcrossItems() {
        RecommendationContext context = buildContext(BigDecimal.valueOf(300), ShoppingDepartment.NO_PREFERENCE);
        RequestedItem jersey = item("USA soccer jersey", GenericItemCategory.TOP, List.of("jersey"), "soccer");
        RequestedItem boots = item("football boots", GenericItemCategory.FOOTWEAR, List.of("boots"), "soccer");

        List<RequestedItemSearchRequest> requests = factory.buildRequests(context, List.of(jersey, boots));

        assertThat(requests).allSatisfy(r -> assertThat(r.request().maxPrice()).isEqualByComparingTo("150.00"));
    }

    @Test
    void buildRequests_usesShoeSizeForFootwearAndClothingSizeOtherwise() {
        RecommendationContext context = buildContext(BigDecimal.valueOf(300), ShoppingDepartment.NO_PREFERENCE);
        RequestedItem jersey = item("USA soccer jersey", GenericItemCategory.TOP, List.of("jersey"), "soccer");
        RequestedItem boots = item("football boots", GenericItemCategory.FOOTWEAR, List.of("boots"), "soccer");

        List<RequestedItemSearchRequest> requests = factory.buildRequests(context, List.of(jersey, boots));

        RetailProductSearchRequest jerseyRequest = requests.stream().filter(r -> r.item().equals(jersey)).findFirst().orElseThrow().request();
        RetailProductSearchRequest bootsRequest = requests.stream().filter(r -> r.item().equals(boots)).findFirst().orElseThrow().request();
        assertThat(jerseyRequest.clothingSize()).isEqualTo("M");
        assertThat(bootsRequest.clothingSize()).isEqualTo("9");
    }

    @Test
    void buildRequests_usesNoSizeHintForAccessoriesAndEquipment() {
        RecommendationContext context = buildContext(BigDecimal.valueOf(300), ShoppingDepartment.NO_PREFERENCE);
        RequestedItem goggles = item("swim goggles", GenericItemCategory.EQUIPMENT, List.of("swim goggles"), "swimming");

        List<RequestedItemSearchRequest> requests = factory.buildRequests(context, List.of(goggles));

        assertThat(requests.get(0).request().clothingSize()).isNull();
    }

    @Test
    void buildRequests_forMenDepartment_setsMenTargetAudienceAndAddsMenKeywords() {
        RecommendationContext context = buildContext(BigDecimal.valueOf(300), ShoppingDepartment.MEN);
        RequestedItem jersey = item("USA soccer jersey", GenericItemCategory.TOP, List.of("jersey"), "soccer");

        List<RequestedItemSearchRequest> requests = factory.buildRequests(context, List.of(jersey));

        assertThat(requests.get(0).request().targetAudience()).isEqualTo(TargetAudience.MEN);
        assertThat(requests.get(0).request().keywords()).contains("men's", "mens");
    }

    @Test
    void buildRequests_forNoPreferenceDepartment_addsNoDepartmentKeyword() {
        RecommendationContext context = buildContext(BigDecimal.valueOf(300), ShoppingDepartment.NO_PREFERENCE);
        RequestedItem jersey = item("USA soccer jersey", GenericItemCategory.TOP, List.of("jersey"), "soccer");

        List<RequestedItemSearchRequest> requests = factory.buildRequests(context, List.of(jersey));

        assertThat(requests.get(0).request().targetAudience()).isEqualTo(TargetAudience.NO_PREFERENCE);
        assertThat(requests.get(0).request().keywords())
                .doesNotContain("men's", "mens", "women's", "womens", "unisex", "gender-neutral");
    }
}

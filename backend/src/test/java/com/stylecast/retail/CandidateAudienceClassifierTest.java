package com.stylecast.retail;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateAudienceClassifierTest {

    // --- classifyFromTitle ---------------------------------------------------

    @Test
    void classifyFromTitle_mensMarker_classifiesAsMen() {
        assertThat(CandidateAudienceClassifier.classifyFromTitle("Classic Trouser (Men)")).isEqualTo(CandidateAudience.MEN);
        assertThat(CandidateAudienceClassifier.classifyFromTitle("Men's Silk Tie")).isEqualTo(CandidateAudience.MEN);
        assertThat(CandidateAudienceClassifier.classifyFromTitle("Mens Silk Tie")).isEqualTo(CandidateAudience.MEN);
    }

    @Test
    void classifyFromTitle_womensMarker_classifiesAsWomen() {
        assertThat(CandidateAudienceClassifier.classifyFromTitle("Floral Dress (Women)")).isEqualTo(CandidateAudience.WOMEN);
        assertThat(CandidateAudienceClassifier.classifyFromTitle("Women's Silk Blouse")).isEqualTo(CandidateAudience.WOMEN);
        assertThat(CandidateAudienceClassifier.classifyFromTitle("Womens Silk Blouse")).isEqualTo(CandidateAudience.WOMEN);
    }

    @Test
    void classifyFromTitle_unisexMarker_classifiesAsUnisex() {
        assertThat(CandidateAudienceClassifier.classifyFromTitle("Unisex Wool Scarf")).isEqualTo(CandidateAudience.UNISEX);
        assertThat(CandidateAudienceClassifier.classifyFromTitle("Gender-Neutral Sneaker")).isEqualTo(CandidateAudience.UNISEX);
        assertThat(CandidateAudienceClassifier.classifyFromTitle("Gender Neutral Sneaker")).isEqualTo(CandidateAudience.UNISEX);
    }

    @Test
    void classifyFromTitle_neutralTitleWithNoMarker_classifiesAsUnknown() {
        assertThat(CandidateAudienceClassifier.classifyFromTitle("Leather Belt")).isEqualTo(CandidateAudience.UNKNOWN);
    }

    @Test
    void classifyFromTitle_nullTitle_classifiesAsUnknown() {
        assertThat(CandidateAudienceClassifier.classifyFromTitle(null)).isEqualTo(CandidateAudience.UNKNOWN);
    }

    @Test
    void classifyFromTitle_conflictingMarkers_classifiesAsUnknownRatherThanGuessing() {
        assertThat(CandidateAudienceClassifier.classifyFromTitle("Men's Tie and Women's Blouse Gift Set"))
                .isEqualTo(CandidateAudience.UNKNOWN);
    }

    @Test
    void classifyFromTitle_womensNeverFalsePositivesAsMensSubstring() {
        // "women's" literally contains the characters "men's" starting at index 2 -
        // must not be misclassified as a men's marker.
        assertThat(CandidateAudienceClassifier.classifyFromTitle("Women's Silk Blouse")).isEqualTo(CandidateAudience.WOMEN);
    }

    // --- isAcceptable ----------------------------------------------------------

    @Test
    void isAcceptable_menRequest_rejectsWomensBalletFlats() {
        CandidateAudience ballerinaFlats = CandidateAudienceClassifier.classifyFromTitle("Ballet Flats (Women)");
        assertThat(ballerinaFlats).isEqualTo(CandidateAudience.WOMEN);
        assertThat(CandidateAudienceClassifier.isAcceptable(ballerinaFlats, TargetAudience.MEN)).isFalse();
    }

    @Test
    void isAcceptable_womenRequest_rejectsMensDressShoes() {
        CandidateAudience dressShoes = CandidateAudienceClassifier.classifyFromTitle("Derby Dress Shoes (Men)");
        assertThat(dressShoes).isEqualTo(CandidateAudience.MEN);
        assertThat(CandidateAudienceClassifier.isAcceptable(dressShoes, TargetAudience.WOMEN)).isFalse();
    }

    @Test
    void isAcceptable_menRequest_acceptsMensProducts() {
        assertThat(CandidateAudienceClassifier.isAcceptable(CandidateAudience.MEN, TargetAudience.MEN)).isTrue();
    }

    @Test
    void isAcceptable_womenRequest_acceptsWomensProducts() {
        assertThat(CandidateAudienceClassifier.isAcceptable(CandidateAudience.WOMEN, TargetAudience.WOMEN)).isTrue();
    }

    @Test
    void isAcceptable_unisexProducts_areAcceptedForAnyRequestedDepartment() {
        assertThat(CandidateAudienceClassifier.isAcceptable(CandidateAudience.UNISEX, TargetAudience.MEN)).isTrue();
        assertThat(CandidateAudienceClassifier.isAcceptable(CandidateAudience.UNISEX, TargetAudience.WOMEN)).isTrue();
        assertThat(CandidateAudienceClassifier.isAcceptable(CandidateAudience.UNISEX, TargetAudience.UNISEX)).isTrue();
        assertThat(CandidateAudienceClassifier.isAcceptable(CandidateAudience.UNISEX, TargetAudience.NO_PREFERENCE)).isTrue();
    }

    @Test
    void isAcceptable_unknownAudience_isNeverRejected() {
        assertThat(CandidateAudienceClassifier.isAcceptable(CandidateAudience.UNKNOWN, TargetAudience.MEN)).isTrue();
        assertThat(CandidateAudienceClassifier.isAcceptable(CandidateAudience.UNKNOWN, TargetAudience.WOMEN)).isTrue();
    }

    @Test
    void isAcceptable_noPreferenceRequest_permitsEitherDepartment() {
        assertThat(CandidateAudienceClassifier.isAcceptable(CandidateAudience.MEN, TargetAudience.NO_PREFERENCE)).isTrue();
        assertThat(CandidateAudienceClassifier.isAcceptable(CandidateAudience.WOMEN, TargetAudience.NO_PREFERENCE)).isTrue();
    }

    @Test
    void isAcceptable_unisexRequest_neverHardRejectsOnDepartmentGrounds() {
        assertThat(CandidateAudienceClassifier.isAcceptable(CandidateAudience.MEN, TargetAudience.UNISEX)).isTrue();
        assertThat(CandidateAudienceClassifier.isAcceptable(CandidateAudience.WOMEN, TargetAudience.UNISEX)).isTrue();
    }
}

-- Partial live-recommendation support (Task 8 follow-up): each generation
-- now records its overall completeness independently of whether a full
-- outfit could be assembled, since required categories are searched
-- independently and valid candidates from categories that succeed are
-- always preserved (see LiveRecommendationService/LiveOutfitAssembler).
--
-- "no_result_reason" is renamed to the more general "message" since it is
-- now populated for PARTIAL results too (which do have items), not only
-- for the empty-outcome case it previously covered exclusively.
ALTER TABLE live_outfit_recommendations
    RENAME COLUMN no_result_reason TO message;

ALTER TABLE live_outfit_recommendations
    ADD COLUMN completeness VARCHAR(20) NOT NULL DEFAULT 'NO_RESULTS'
        CHECK (completeness IN ('COMPLETE', 'PARTIAL', 'NO_RESULTS', 'PROVIDER_UNAVAILABLE')),
    ADD COLUMN found_categories VARCHAR(300),
    ADD COLUMN missing_categories VARCHAR(300);

ALTER TABLE live_outfit_recommendations
    ALTER COLUMN completeness DROP DEFAULT;

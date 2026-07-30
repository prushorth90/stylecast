-- Explicit requested-item extraction (Task 8.5): preserves the user's exact
-- product phrases (e.g. "USA soccer jersey") instead of collapsing them into
-- broad catalog categories. Child rows of event_occasion_interpretations,
-- rebuilt (delete + re-insert) every time the interpretation is generated or
-- regenerated - mirrors that row's own overwrite-in-place lifecycle.
--
-- Existing interpretation rows simply get zero matching rows here; the
-- application already treats an empty requested-items list as "no explicit
-- items were extracted" (falls back to the existing required-categories
-- template flow), so this is fully backward compatible with no backfill.
CREATE TABLE occasion_requested_items (
    id UUID PRIMARY KEY,
    interpretation_id UUID NOT NULL REFERENCES event_occasion_interpretations(id) ON DELETE CASCADE,
    original_phrase VARCHAR(200) NOT NULL,
    -- Broad, activity-agnostic category only - see GenericItemCategory. Never
    -- one enum value per sport/garment; specificity lives in original_phrase
    -- and search_terms instead.
    generic_category VARCHAR(20) NOT NULL
        CHECK (generic_category IN (
            'TOP', 'BOTTOM', 'ONE_PIECE', 'FOOTWEAR', 'OUTERWEAR', 'ACCESSORY', 'EQUIPMENT', 'OTHER'
        )),
    search_terms TEXT[] NOT NULL,
    required BOOLEAN NOT NULL DEFAULT TRUE,
    activity_context VARCHAR(100),
    display_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_occasion_requested_items_interpretation_id
    ON occasion_requested_items (interpretation_id);

-- Task 8.5: explicit requested-item support for the live-Nordstrom
-- recommendation pipeline.
--
-- live_outfit_recommendations gains the requested-item counterpart of the
-- existing found_categories/missing_categories columns, denormalized the
-- same way across every row of one generation. Stored as plain TEXT
-- (application-encoded) rather than a native array/JSON column, kept
-- consistent with the existing comma-joined found_categories/
-- missing_categories columns in this same table.
ALTER TABLE live_outfit_recommendations
    ADD COLUMN found_requested_items TEXT,
    ADD COLUMN missing_requested_items TEXT;

-- live_outfit_items.category becomes optional: an item produced by the new
-- explicit-item pipeline has no natural catalog ProductCategory (forcing one
-- would reintroduce exactly the specificity-loss problem this task fixes),
-- so it instead carries requested_item_id/requested_item_phrase/
-- requested_item_generic_category. An item from the existing
-- required-categories pipeline is unaffected - it keeps populating
-- `category` exactly as before, and the three new columns stay NULL.
--
-- The existing `category IN (...)` CHECK constraint from
-- V11__create_live_outfit_recommendation_tables.sql is unaffected: a NULL
-- value always satisfies an IN-list CHECK in Postgres.
ALTER TABLE live_outfit_items
    ALTER COLUMN category DROP NOT NULL,
    ADD COLUMN requested_item_id UUID,
    ADD COLUMN requested_item_phrase VARCHAR(200),
    ADD COLUMN requested_item_generic_category VARCHAR(20)
        CHECK (requested_item_generic_category IN (
            'TOP', 'BOTTOM', 'ONE_PIECE', 'FOOTWEAR', 'OUTERWEAR', 'ACCESSORY', 'EQUIPMENT', 'OTHER'
        ));

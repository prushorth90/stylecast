-- Adds product-detail-enrichment columns to live_outfit_items (Task 8
-- follow-up): brand/original_price/color/available_sizes/stock_text are
-- populated only when OpenAiProductDetailEnricher independently confirmed
-- them for a candidate's exact product URL; availability_verified mirrors
-- price_verified/size_verified (added in V11) - always false unless
-- explicitly confirmed, never set true just because a value is present.
ALTER TABLE live_outfit_items
    ADD COLUMN brand VARCHAR(150),
    ADD COLUMN original_price NUMERIC(10, 2) CHECK (original_price IS NULL OR original_price > 0),
    ADD COLUMN color VARCHAR(50),
    ADD COLUMN available_sizes VARCHAR(300),
    ADD COLUMN stock_text VARCHAR(100),
    ADD COLUMN availability_verified BOOLEAN NOT NULL DEFAULT false;

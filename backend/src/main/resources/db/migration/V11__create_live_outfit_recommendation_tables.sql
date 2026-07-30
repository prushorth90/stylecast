-- Live-Nordstrom-sourced outfit recommendations (Task 8), kept as separate
-- tables from outfit_recommendations/outfit_items (Task 7A) because live
-- items reference external Nordstrom product pages (URL, unverified
-- price/size) instead of local catalog product/variant ids - the two
-- shapes are not interchangeable.
--
-- Regeneration is versioned via "generation", same scheme as
-- outfit_recommendations: the previous generation's ACTIVE rows are marked
-- SUPERSEDED rather than deleted, and exactly one NO_VALID_OUTFIT row (no
-- items) is persisted for a generation that found no complete outfit. A
-- generation attempt that fails outright (the live provider throws)
-- persists nothing at all.
CREATE TABLE live_outfit_recommendations (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    generation INTEGER NOT NULL,
    name VARCHAR(150) NOT NULL,
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('ACTIVE', 'SUPERSEDED', 'NO_VALID_OUTFIT')),
    source VARCHAR(20) NOT NULL
        CHECK (source IN ('LIVE_NORDSTROM')),
    rank_position INTEGER,
    no_result_reason VARCHAR(500),
    explanation VARCHAR(500),
    generated_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_live_outfit_recommendations_event_generation ON live_outfit_recommendations (event_id, generation);
CREATE INDEX idx_live_outfit_recommendations_event_status ON live_outfit_recommendations (event_id, status);

CREATE TABLE live_outfit_items (
    id UUID PRIMARY KEY,
    recommendation_id UUID NOT NULL REFERENCES live_outfit_recommendations (id) ON DELETE CASCADE,
    category VARCHAR(20) NOT NULL
        CHECK (category IN ('BLAZER', 'SUIT', 'SHIRT', 'POLO', 'TROUSERS', 'DRESS', 'SKIRT', 'SHOES', 'OUTERWEAR', 'ACCESSORY')),
    retailer VARCHAR(20) NOT NULL
        CHECK (retailer IN ('NORDSTROM')),
    title VARCHAR(300),
    product_url VARCHAR(1000) NOT NULL,
    image_url VARCHAR(1000),
    price NUMERIC(10, 2) CHECK (price IS NULL OR price > 0),
    currency VARCHAR(10),
    -- Always false unless a future provider independently confirms price/size -
    -- see LiveOutfitItem; never set true just because a value is present.
    price_verified BOOLEAN NOT NULL DEFAULT false,
    requested_size VARCHAR(50),
    size_verified BOOLEAN NOT NULL DEFAULT false,
    source_citation VARCHAR(300),
    display_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- A single outfit never links to the same Nordstrom product page twice.
    CONSTRAINT uq_live_outfit_item_product_url UNIQUE (recommendation_id, product_url)
);

CREATE INDEX idx_live_outfit_items_recommendation_id ON live_outfit_items (recommendation_id);

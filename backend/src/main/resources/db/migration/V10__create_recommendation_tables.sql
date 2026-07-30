-- Deterministic outfit recommendations, local-catalog only (Task 7A).
--
-- Regeneration is versioned via "generation" (an increasing integer per
-- event); the previous generation's ACTIVE rows are marked SUPERSEDED
-- rather than deleted, so history is preserved. Exactly one row with
-- status NO_VALID_OUTFIT (no items) is persisted for a generation that
-- found no valid outfit, so a repeated GET can tell "generated, found
-- nothing" apart from "never generated" without re-running generation.
CREATE TABLE outfit_recommendations (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    generation INTEGER NOT NULL,
    name VARCHAR(150) NOT NULL,
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('ACTIVE', 'SUPERSEDED', 'NO_VALID_OUTFIT')),
    source VARCHAR(20) NOT NULL
        CHECK (source IN ('LOCAL_CATALOG')),
    rank_position INTEGER,
    total_price NUMERIC(10, 2) NOT NULL DEFAULT 0 CHECK (total_price >= 0),
    occasion_fit_score INTEGER NOT NULL DEFAULT 0 CHECK (occasion_fit_score BETWEEN 0 AND 100),
    weather_fit_score INTEGER NOT NULL DEFAULT 0 CHECK (weather_fit_score BETWEEN 0 AND 100),
    style_fit_score INTEGER NOT NULL DEFAULT 0 CHECK (style_fit_score BETWEEN 0 AND 100),
    color_fit_score INTEGER NOT NULL DEFAULT 0 CHECK (color_fit_score BETWEEN 0 AND 100),
    budget_efficiency_score INTEGER NOT NULL DEFAULT 0 CHECK (budget_efficiency_score BETWEEN 0 AND 100),
    completeness_score INTEGER NOT NULL DEFAULT 0 CHECK (completeness_score BETWEEN 0 AND 100),
    overall_score INTEGER NOT NULL DEFAULT 0 CHECK (overall_score BETWEEN 0 AND 100),
    no_result_reason VARCHAR(500),
    explanation VARCHAR(500),
    generated_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_outfit_recommendations_event_generation ON outfit_recommendations (event_id, generation);
CREATE INDEX idx_outfit_recommendations_event_status ON outfit_recommendations (event_id, status);

CREATE TABLE outfit_items (
    id UUID PRIMARY KEY,
    recommendation_id UUID NOT NULL REFERENCES outfit_recommendations (id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products (id),
    product_variant_id UUID NOT NULL REFERENCES product_variants (id),
    category VARCHAR(20) NOT NULL
        CHECK (category IN ('BLAZER', 'SUIT', 'SHIRT', 'POLO', 'TROUSERS', 'DRESS', 'SKIRT', 'SHOES', 'OUTERWEAR', 'ACCESSORY')),
    item_price NUMERIC(10, 2) NOT NULL CHECK (item_price > 0),
    display_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- A single outfit never selects the same product, or the same variant, twice.
    CONSTRAINT uq_outfit_item_product UNIQUE (recommendation_id, product_id),
    CONSTRAINT uq_outfit_item_variant UNIQUE (recommendation_id, product_variant_id)
);

CREATE INDEX idx_outfit_items_recommendation_id ON outfit_items (recommendation_id);

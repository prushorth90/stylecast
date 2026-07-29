-- Event occasion interpretations: one current interpretation per event (Task 6).
--
-- Category/requirement columns store enum names as text[], the same
-- pattern used by event_style_preferences.preferred_colors, rather than a
-- native Postgres enum array.
CREATE TABLE event_occasion_interpretations (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE REFERENCES events(id) ON DELETE CASCADE,
    occasion VARCHAR(30) NOT NULL,
    dress_code VARCHAR(30) NOT NULL,
    formality_level INTEGER NOT NULL
        CHECK (formality_level BETWEEN 1 AND 10),
    required_categories TEXT[] NOT NULL,
    optional_categories TEXT[] NOT NULL,
    preferred_colors TEXT[] NOT NULL,
    colors_to_avoid TEXT[] NOT NULL,
    special_requirements TEXT[] NOT NULL,
    assumptions TEXT[] NOT NULL,
    confidence NUMERIC(3, 2) NOT NULL
        CHECK (confidence BETWEEN 0 AND 1),
    source VARCHAR(30) NOT NULL
        CHECK (source IN ('AI', 'RULE_BASED_FALLBACK')),
    -- Optional AI model identifier (e.g. "gpt-4.1"); NULL for a rule-based
    -- fallback result. Never stores an API key or raw provider response.
    model_name VARCHAR(100),
    generated_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Event styling preferences: one record per event (Task 3).
CREATE TABLE event_style_preferences (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE REFERENCES events(id) ON DELETE CASCADE,
    outfit_request TEXT NOT NULL,
    max_budget NUMERIC(10, 2) NOT NULL CHECK (max_budget > 0),
    clothing_size VARCHAR(50) NOT NULL,
    shoe_size VARCHAR(20) NOT NULL,
    preferred_style VARCHAR(20) NOT NULL
        CHECK (preferred_style IN ('CLASSIC', 'MODERN', 'MINIMAL', 'BOLD', 'CASUAL', 'FORMAL')),
    preferred_colors TEXT[] NOT NULL DEFAULT '{}',
    colors_to_avoid TEXT[] NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

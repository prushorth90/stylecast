-- Adds a normalized department/audience classification to each persisted
-- live outfit item (candidate department/audience filtering). No existing
-- rows are expected in production yet for this new live-recommendation
-- feature, but a safe default is still applied defensively so startup
-- never fails against any pre-existing row.
ALTER TABLE live_outfit_items
    ADD COLUMN audience VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN'
        CHECK (audience IN ('MEN', 'WOMEN', 'UNISEX', 'UNKNOWN'));

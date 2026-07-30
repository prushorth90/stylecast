ALTER TABLE live_outfit_recommendations
    ADD COLUMN stale BOOLEAN NOT NULL DEFAULT FALSE;

-- Authentication (Task 17): every event now belongs to exactly one
-- app_users row.
--
-- Migration behavior (documented per explicit product decision): existing
-- local events were created before authentication existed and have no
-- owner. This is pre-launch local/development data only - there are no
-- real user accounts to preserve - so it is discarded outright
-- (TRUNCATE ... CASCADE, which also clears every dependent per-event table:
-- preferences, weather snapshots, occasion interpretations + requested
-- items, local and live outfit recommendations + items) rather than
-- backfilled to a placeholder owner. This keeps `events.user_id` a clean,
-- always-NOT-NULL foreign key from the very first row, with no
-- permanently-orphaned legacy data to reason about later. Do NOT reuse this
-- migration as a template for a real production cutover - a production
-- migration with real user data would need a backfill/reassignment
-- strategy instead of a truncate.
TRUNCATE TABLE events CASCADE;

ALTER TABLE events
    ADD COLUMN user_id UUID NOT NULL REFERENCES app_users (id);

CREATE INDEX idx_events_user_id ON events (user_id);

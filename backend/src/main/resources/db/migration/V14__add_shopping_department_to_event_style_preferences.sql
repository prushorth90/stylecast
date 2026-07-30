-- Adds the required shoppingDepartment field to event styling preferences
-- (Task: candidate department/audience filtering). Existing rows are safely
-- backfilled with 'NO_PREFERENCE' (no department restriction) via the
-- column default - no existing user data is reset or deleted, and the
-- default is kept (not dropped) as a defense-in-depth fallback for any row
-- ever written outside the validated API.
ALTER TABLE event_style_preferences
    ADD COLUMN shopping_department VARCHAR(20) NOT NULL DEFAULT 'NO_PREFERENCE'
        CHECK (shopping_department IN ('MEN', 'WOMEN', 'UNISEX', 'NO_PREFERENCE'));

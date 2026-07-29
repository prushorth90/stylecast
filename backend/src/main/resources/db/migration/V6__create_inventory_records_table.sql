-- Catalog: inventory per variant, per location (Task 4A).
--
-- A variant may have more than one inventory_records row (one per fulfillment
-- location); a variant is in stock when at least one of its rows has
-- quantity > 0. This MVP's seed data only uses a single fictional location.
CREATE TABLE inventory_records (
    id UUID PRIMARY KEY,
    product_variant_id UUID NOT NULL REFERENCES product_variants (id) ON DELETE CASCADE,
    location VARCHAR(60) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_inventory_variant_location UNIQUE (product_variant_id, location)
);

CREATE INDEX idx_inventory_records_variant_id ON inventory_records (product_variant_id);

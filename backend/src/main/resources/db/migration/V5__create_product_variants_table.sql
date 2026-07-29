-- Catalog: product variants (size/color combinations) (Task 4A).
CREATE TABLE product_variants (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    sku VARCHAR(64) NOT NULL UNIQUE,
    clothing_size VARCHAR(20) NOT NULL,
    color VARCHAR(40) NOT NULL,
    price_override NUMERIC(10, 2) CHECK (price_override IS NULL OR price_override > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_product_variant_size_color UNIQUE (product_id, clothing_size, color)
);

CREATE INDEX idx_product_variants_product_id ON product_variants (product_id);
CREATE INDEX idx_product_variants_clothing_size ON product_variants (clothing_size);
CREATE INDEX idx_product_variants_color ON product_variants (color);

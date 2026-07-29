-- Catalog: products table and product tags (Task 4A).
--
-- Occasion/style/weather tags use small normalized join tables (rather than
-- array columns) so multi-valued tag filters compose as ordinary joins with
-- a DISTINCT root query, matching the "products" / "style_tags" /
-- "product_style_tags" table shapes anticipated in docs/ARCHITECTURE.md.
CREATE TABLE products (
    id UUID PRIMARY KEY,
    brand VARCHAR(120) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    category VARCHAR(20) NOT NULL
        CHECK (category IN ('BLAZER', 'SUIT', 'SHIRT', 'POLO', 'TROUSERS', 'DRESS', 'SKIRT', 'SHOES', 'OUTERWEAR', 'ACCESSORY')),
    base_price NUMERIC(10, 2) NOT NULL CHECK (base_price > 0),
    image_url VARCHAR(500),
    formality_level INTEGER NOT NULL CHECK (formality_level BETWEEN 1 AND 10),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_products_category ON products (category);
CREATE INDEX idx_products_formality_level ON products (formality_level);
CREATE INDEX idx_products_active ON products (active);

CREATE TABLE product_occasion_tags (
    product_id UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    occasion_tag VARCHAR(20) NOT NULL
        CHECK (occasion_tag IN ('WEDDING', 'INTERVIEW', 'DINNER', 'NETWORKING', 'CONCERT', 'CASUAL', 'FORMAL_EVENT')),
    PRIMARY KEY (product_id, occasion_tag)
);

CREATE TABLE product_style_tags (
    product_id UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    style_tag VARCHAR(20) NOT NULL
        CHECK (style_tag IN ('CLASSIC', 'MODERN', 'MINIMAL', 'BOLD', 'CASUAL', 'FORMAL')),
    PRIMARY KEY (product_id, style_tag)
);

CREATE TABLE product_weather_tags (
    product_id UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    weather_tag VARCHAR(10) NOT NULL
        CHECK (weather_tag IN ('HOT', 'MILD', 'COLD', 'RAIN', 'WIND')),
    PRIMARY KEY (product_id, weather_tag)
);

CREATE INDEX idx_product_occasion_tags_tag ON product_occasion_tags (occasion_tag);
CREATE INDEX idx_product_style_tags_tag ON product_style_tags (style_tag);
CREATE INDEX idx_product_weather_tags_tag ON product_weather_tags (weather_tag);

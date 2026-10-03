CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX ix_product_name_trgm ON product USING gin (lower(name) gin_trgm_ops);
CREATE INDEX ix_product_sku_trgm ON product USING gin (lower(sku) gin_trgm_ops);
CREATE INDEX ix_product_description_trgm ON product USING gin (lower(description) gin_trgm_ops);
CREATE INDEX ix_product_category_trgm ON product USING gin (lower(category) gin_trgm_ops);
CREATE INDEX ix_product_active_category_name ON product (active, lower(category), name, id);

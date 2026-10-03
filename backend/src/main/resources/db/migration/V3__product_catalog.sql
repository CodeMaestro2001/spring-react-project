CREATE TABLE product (
    id UUID PRIMARY KEY,
    sku VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(2000),
    category VARCHAR(80) NOT NULL,
    unit VARCHAR(32) NOT NULL,
    price NUMERIC(12, 2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_product_sku UNIQUE (sku),
    CONSTRAINT ck_product_price_positive CHECK (price > 0),
    CONSTRAINT ck_product_currency_code CHECK (currency_code = upper(currency_code))
);

CREATE INDEX ix_product_active_name ON product (active, name);
CREATE INDEX ix_product_category ON product (category);

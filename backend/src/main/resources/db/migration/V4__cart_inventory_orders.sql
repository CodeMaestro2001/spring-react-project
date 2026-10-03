ALTER TABLE product
    ADD COLUMN stock_quantity INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_product_stock_non_negative CHECK (stock_quantity BETWEEN 0 AND 10000000);

CREATE TABLE shopping_cart (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES account(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_shopping_cart_account UNIQUE (account_id)
);

CREATE TABLE cart_item (
    id UUID PRIMARY KEY,
    cart_id UUID NOT NULL REFERENCES shopping_cart(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES product(id),
    quantity INTEGER NOT NULL,
    CONSTRAINT uq_cart_item_product UNIQUE (cart_id, product_id),
    CONSTRAINT ck_cart_item_quantity CHECK (quantity BETWEEN 1 AND 99)
);

CREATE TABLE customer_order (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES account(id),
    customer_email VARCHAR(254) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    total_amount NUMERIC(14, 2) NOT NULL,
    placed_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_order_idempotency UNIQUE (account_id, idempotency_key),
    CONSTRAINT ck_order_status CHECK (status IN ('PLACED', 'PROCESSING', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_order_total CHECK (total_amount >= 0)
);

CREATE TABLE order_item (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES customer_order(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES product(id),
    product_sku VARCHAR(64) NOT NULL,
    product_name VARCHAR(160) NOT NULL,
    unit VARCHAR(32) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    line_total NUMERIC(14, 2) NOT NULL,
    CONSTRAINT ck_order_item_quantity CHECK (quantity BETWEEN 1 AND 99),
    CONSTRAINT ck_order_item_unit_price CHECK (unit_price > 0),
    CONSTRAINT ck_order_item_line_total CHECK (line_total > 0)
);

CREATE INDEX ix_cart_item_cart ON cart_item (cart_id);
CREATE INDEX ix_customer_order_account_placed ON customer_order (account_id, placed_at DESC);
CREATE INDEX ix_customer_order_status_placed ON customer_order (status, placed_at DESC);
CREATE INDEX ix_order_item_order ON order_item (order_id);

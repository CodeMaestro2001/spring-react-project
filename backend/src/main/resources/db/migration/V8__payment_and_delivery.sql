ALTER TABLE customer_order
    ADD COLUMN payment_method VARCHAR(30) NOT NULL DEFAULT 'BANK_TRANSFER',
    ADD COLUMN payment_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN delivery_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN delivery_recipient VARCHAR(120) NOT NULL DEFAULT 'Customer',
    ADD COLUMN delivery_address VARCHAR(500) NOT NULL DEFAULT 'Address not provided',
    ADD COLUMN delivery_phone VARCHAR(30),
    ADD COLUMN delivery_note VARCHAR(500),
    ADD CONSTRAINT ck_order_payment_method CHECK (payment_method IN ('BANK_TRANSFER', 'CASH_ON_DELIVERY')),
    ADD CONSTRAINT ck_order_payment_status CHECK (payment_status IN ('PENDING', 'PAID', 'FAILED', 'REFUNDED')),
    ADD CONSTRAINT ck_order_delivery_status CHECK (delivery_status IN ('PENDING', 'PREPARING', 'DISPATCHED', 'DELIVERED', 'CANCELLED'));

CREATE INDEX ix_customer_order_delivery_status_placed ON customer_order (delivery_status, placed_at DESC);

CREATE TABLE account (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    full_name VARCHAR(120) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_account_email UNIQUE (email),
    CONSTRAINT ck_account_role CHECK (role IN ('CUSTOMER', 'ADMIN'))
);

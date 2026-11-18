CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    email         VARCHAR(254) NOT NULL UNIQUE,
    display_name  VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE device_tokens (
    token      VARCHAR(4096) PRIMARY KEY,
    user_id    UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX device_tokens_user_id_idx ON device_tokens (user_id);

CREATE TABLE payment_requests (
    id            UUID         PRIMARY KEY,
    sender_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    recipient_id  UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    client_id     UUID,
    amount        BIGINT       NOT NULL CHECK (amount > 0),
    shop_name     VARCHAR(100),
    purchase_date DATE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT payment_requests_sender_client_uq UNIQUE (sender_id, client_id),
    CONSTRAINT payment_requests_not_self CHECK (sender_id <> recipient_id)
);

CREATE INDEX payment_requests_recipient_idx ON payment_requests (recipient_id, created_at DESC);
CREATE INDEX payment_requests_sender_idx ON payment_requests (sender_id);

CREATE TABLE payment_request_items (
    request_id UUID         NOT NULL REFERENCES payment_requests (id) ON DELETE CASCADE,
    line_no    INTEGER      NOT NULL,
    name       VARCHAR(200) NOT NULL,
    amount     BIGINT       NOT NULL CHECK (amount >= 0),
    PRIMARY KEY (request_id, line_no)
);

CREATE TABLE tariffs
(
    id             UUID                     NOT NULL,
    name           VARCHAR(100)             NOT NULL,
    description    VARCHAR(500),
    category       VARCHAR(32)              NOT NULL,
    price          DECIMAL(19, 2)           NOT NULL,
    currency       VARCHAR(3)               NOT NULL DEFAULT 'RUB',
    billing_period VARCHAR(32)              NOT NULL DEFAULT 'MONTH',
    active         BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_tariffs PRIMARY KEY (id),
    CONSTRAINT ck_tariffs_category CHECK (category IN ('INTERNET', 'TV', 'MOBILE')),
    CONSTRAINT ck_tariffs_billing_period CHECK (billing_period IN ('MONTH')),
    CONSTRAINT ck_tariffs_price_positive CHECK (price > 0)
);

CREATE INDEX idx_tariffs_active_category ON tariffs (active, category);
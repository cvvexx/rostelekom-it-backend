CREATE TABLE subscriptions
(
    id                   UUID                     NOT NULL,
    user_id              UUID                     NOT NULL,
    tariff_id            UUID                     NOT NULL REFERENCES tariffs (id),
    status               VARCHAR(32)              NOT NULL DEFAULT 'ACTIVE',
    price                DECIMAL(19, 2)           NOT NULL,
    currency             VARCHAR(3)               NOT NULL DEFAULT 'RUB',
    current_period_start TIMESTAMP WITH TIME ZONE NOT NULL,
    next_charge_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    cancel_at_period_end BOOLEAN                  NOT NULL DEFAULT FALSE,
    canceled_at          TIMESTAMP WITH TIME ZONE,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_subscriptions PRIMARY KEY (id),
    CONSTRAINT ck_subscriptions_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CANCELED')),
    CONSTRAINT ck_subscriptions_price_positive CHECK (price > 0)
);

CREATE UNIQUE INDEX uk_subscriptions_user_active_tariff
    ON subscriptions (user_id, tariff_id)
    WHERE status IN ('ACTIVE', 'SUSPENDED');

CREATE INDEX idx_subscriptions_user_id_created_at
    ON subscriptions (user_id, created_at DESC);

CREATE INDEX idx_subscriptions_due_for_charge
    ON subscriptions (next_charge_at)
    WHERE status IN ('ACTIVE', 'SUSPENDED');
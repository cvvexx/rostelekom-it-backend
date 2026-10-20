create table payments
(
    id                  uuid primary key,
    account_id          uuid                     not null references accounts (id),
    amount              numeric(19, 2)           not null,
    currency            varchar(3)               not null default 'RUB',
    status              varchar(32)              not null default 'PENDING',
    provider            varchar(32)              not null,
    provider_payment_id varchar(64),
    confirmation_url    varchar(2048),
    idempotency_key     varchar(64)              not null,
    created_at          timestamp with time zone not null default now(),
    updated_at          timestamp with time zone not null default now(),

    constraint uk_payments_account_id_idempotency_key unique (account_id, idempotency_key),
    constraint uk_payments_provider_payment_id unique (provider, provider_payment_id),
    constraint ck_payments_status check (status in ('PENDING', 'SUCCEEDED', 'CANCELED')),
    constraint ck_payments_amount_positive check (amount > 0)
);

create index idx_payments_status_created_at on payments (status, created_at);

alter table transactions
    add column payment_id uuid references payments (id);

create unique index uk_transactions_payment_id_top_up on transactions (payment_id) where type = 'TOP_UP';

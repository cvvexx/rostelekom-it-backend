create table transactions
(
    id            uuid primary key,
    account_id    uuid                     not null references accounts (id),
    type          varchar(32)              not null,
    amount        numeric(19, 2)           not null,
    balance_after numeric(19, 2)           not null,
    description   varchar(255),
    created_at    timestamp with time zone not null default now(),

    constraint ck_transactions_type check (type in ('TOP_UP', 'CHARGE', 'REFUND')),
    constraint ck_transactions_amount_positive check (amount > 0)
);

create index idx_transactions_account_id_created_at on transactions (account_id, created_at desc);

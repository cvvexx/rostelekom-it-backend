create table accounts
(
    id         uuid primary key,
    user_id    uuid                     not null,
    number     varchar(20)              not null,
    balance    numeric(19, 2)           not null default 0,
    currency   varchar(3)               not null default 'RUB',
    status     varchar(32)              not null default 'ACTIVE',
    version    bigint                   not null default 0,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),

    constraint uk_accounts_user_id unique (user_id),
    constraint uk_accounts_number unique (number),
    constraint ck_accounts_status check (status in ('ACTIVE', 'BLOCKED', 'CLOSED'))
);

create sequence account_number_seq start with 1000000001;

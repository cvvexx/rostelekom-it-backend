alter table transactions
    add column idempotency_key varchar(64) default null;

create unique index uk_transactions_account_idempotency_charge
    on transactions (account_id, idempotency_key) where type = 'CHARGE'

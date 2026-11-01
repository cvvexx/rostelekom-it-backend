alter table payments
    drop constraint ck_payments_status;

alter table payments
    add constraint ck_payments_status check (status in ('PENDING', 'SUCCEEDED', 'CANCELED', 'REFUND_REQUIRED'))
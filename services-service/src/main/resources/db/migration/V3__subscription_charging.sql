-- charge_locked_until: lease джобы списаний (пока не истёк, подписку не берёт другой инстанс)
-- и backoff после отказа billing (нет денег, счёт заблокирован)
ALTER TABLE subscriptions
    ADD COLUMN charge_locked_until        TIMESTAMP WITH TIME ZONE,
    ADD COLUMN last_charge_transaction_id UUID;

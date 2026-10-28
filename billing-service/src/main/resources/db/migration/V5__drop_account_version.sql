-- Optimistic lock (@Version) на счёте убран: все изменения счёта (статус, баланс) идут
-- под пессимистичной блокировкой строки (AccountRepository.findBy...ForUpdate), version лишь дублировал её,
-- а его OptimisticLockException не обрабатывался и превращался в 500.
alter table accounts
    drop column version;

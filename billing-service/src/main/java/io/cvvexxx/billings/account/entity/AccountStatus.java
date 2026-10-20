package io.cvvexxx.billings.account.entity;

//Статус лицевого счёта
public enum AccountStatus {
    ACTIVE,
    BLOCKED, //Ушёл в минус или заблокирован вручную: услуги не оказываются, пополнять можно
    CLOSED
}

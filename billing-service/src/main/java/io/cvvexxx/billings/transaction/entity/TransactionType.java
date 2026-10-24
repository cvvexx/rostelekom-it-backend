package io.cvvexxx.billings.transaction.entity;

//Тип операции по счёту
public enum TransactionType {
    TOP_UP, //Пополнение (+)
    CHARGE, //Списание, например абонентская плата (-)
    REFUND  //Возврат списанного (+)
}

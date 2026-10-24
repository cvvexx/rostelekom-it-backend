package io.cvvexxx.billings.payment.entity;

//Статус платежа
public enum PaymentStatus {
    PENDING,   //Создан, ждём оплату
    SUCCEEDED, //Оплачен, деньги зачислены
    CANCELED   //Отменён или не прошёл
}

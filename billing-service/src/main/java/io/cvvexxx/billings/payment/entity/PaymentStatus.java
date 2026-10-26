package io.cvvexxx.billings.payment.entity;

//Статус платежа
public enum PaymentStatus {
    PENDING,   //Создан, ждём оплату
    SUCCEEDED, //Оплачен, деньги зачислены
    CANCELED,   //Отменён или не прошёл
    REFUND_REQUIRED //Провайдер прислал подтверждение, когда счёт уже закрыли
}

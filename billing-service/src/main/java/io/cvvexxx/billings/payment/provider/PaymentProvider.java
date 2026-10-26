package io.cvvexxx.billings.payment.provider;

import io.cvvexxx.billings.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;

//Общий интерфейс платёжных провайдеров, сейчас есть только StubPaymentProvider
public interface PaymentProvider {

    String name();//Имя провайдера, сохраняется в платеже

    //Создаёт платёж у провайдера, возвращает его id и ссылку на оплату
    ProviderPayment create(UUID paymentId, BigDecimal amount, String currency, String idempotencyKey);

    //Спрашивает у провайдера актуальный статус платежа
    ProviderStatus fetchStatus(String providerPaymentId);

    //Просит провайдера отменить платёж, возвращает итоговый статус у провайдера.
    //SUCCEEDED - юзер успел оплатить и деньги уже у провайдера, PENDING - провайдер отменить не смог
    ProviderStatus cancel(String providerPaymentId);
}

package io.cvvexxx.billings.payment.provider;

//Что вернул провайдер при создании платежа
public record ProviderPayment(
        String providerPaymentId,
        String confirmationUrl
) {
}

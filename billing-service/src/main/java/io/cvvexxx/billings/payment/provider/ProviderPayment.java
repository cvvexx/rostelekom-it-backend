package io.cvvexxx.billings.payment.provider;

public record ProviderPayment(
        String providerPaymentId,
        String confirmationUrl
) {
}

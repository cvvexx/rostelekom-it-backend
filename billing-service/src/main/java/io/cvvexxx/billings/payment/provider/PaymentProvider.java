package io.cvvexxx.billings.payment.provider;

import io.cvvexxx.billings.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentProvider {

    String name();

    ProviderPayment create(UUID paymentId, BigDecimal amount, String currency, String idempotencyKey);

    PaymentStatus fetchStatus(String providerPaymentId);
}

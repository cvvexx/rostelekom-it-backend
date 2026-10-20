package io.cvvexxx.billings.payment.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentTest {

    @Test
    @DisplayName("Новый платёж по умолчанию PENDING в рублях и не финальный")
    void newPaymentDefaults() {
        Payment payment = Payment.builder().build();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getCurrency()).isEqualTo("RUB");
        assertThat(payment.isFinal()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("Любой статус кроме PENDING — финальный")
    void nonPendingStatusIsFinal(PaymentStatus status) {
        assertThat(Payment.builder().status(status).build().isFinal()).isTrue();
    }
}

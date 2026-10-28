package io.cvvexxx.billings.payment.provider.stub;

import io.cvvexxx.billings.payment.provider.ProviderPayment;

import io.cvvexxx.billings.payment.entity.PaymentStatus;
import io.cvvexxx.billings.payment.provider.ProviderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StubPaymentProviderTest {

    private final StubPaymentProvider provider = new StubPaymentProvider();

    @Test
    @DisplayName("create: id выводится из нашего id платежа, повтор отдаёт тот же платёж, ссылка ведёт на StubPaymentController")
    void create_isDeterministic() {
        UUID paymentId = UUID.randomUUID();

        ProviderPayment first = provider.create(paymentId, new BigDecimal("10.00"), "RUB", "key-1");
        ProviderPayment second = provider.create(paymentId, new BigDecimal("10.00"), "RUB", "key-1");

        assertThat(first.providerPaymentId()).isEqualTo("stub-" + paymentId);
        assertThat(first.confirmationUrl()).isEqualTo("/api/billing/stub/pay/stub-" + paymentId);
        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("fetchStatus: PENDING до markPaid, SUCCEEDED после, другие платежи не задевает")
    void fetchStatus_followsMarkPaid() {
        String paid = provider.create(UUID.randomUUID(), BigDecimal.TEN, "RUB", "k1").providerPaymentId();
        String other = provider.create(UUID.randomUUID(), BigDecimal.TEN, "RUB", "k2").providerPaymentId();

        assertThat(provider.fetchStatus(paid)).isEqualTo(ProviderStatus.PENDING);
        provider.markPaid(paid);

        assertThat(provider.fetchStatus(paid)).isEqualTo(ProviderStatus.SUCCEEDED);
        assertThat(provider.fetchStatus(other)).isEqualTo(ProviderStatus.PENDING);
    }
}

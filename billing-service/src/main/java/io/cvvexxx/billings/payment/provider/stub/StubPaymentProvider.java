package io.cvvexxx.billings.payment.provider.stub;

import io.cvvexxx.billings.payment.entity.PaymentStatus;
import io.cvvexxx.billings.payment.provider.PaymentProvider;
import io.cvvexxx.billings.payment.provider.ProviderPayment;
import io.cvvexxx.billings.payment.provider.ProviderStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

//Имитация провайдера: юзер "платит" через StubPaymentController, оплаченные платежи живут в памяти до рестарта
@Component
//Бин создается только если в .properties указан app.payment.provider=stub
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "stub")
public class StubPaymentProvider implements PaymentProvider {

    private final Set<String> paid = ConcurrentHashMap.newKeySet();

    //получаем имя провайдера (STUB - заглушка)
    @Override
    public String name() {
        return "STUB";
    }

    //Создаем в методе url подтверждения оплаты и айдишник платежа у провайдера(на проде это будет делать сам провайдер)
    @Override
    public ProviderPayment create(UUID paymentId, BigDecimal amount, String currency, String idempotencyKey) {
        String providerPaymentId = "stub-" + paymentId;
        return new ProviderPayment(providerPaymentId, StubPaymentController.PATH + "/" + providerPaymentId);
    }

    //На проде спрашиваем у провайдера оплаты статус платежа
    @Override
    public ProviderStatus fetchStatus(String providerPaymentId) {
        return paid.contains(providerPaymentId) ? ProviderStatus.SUCCEEDED : ProviderStatus.PENDING;
    }

    //Как и реальный провайдер, после отмены ссылку на оплату не блокирует: так можно воспроизвести гонку оплаты и отмены
    @Override
    public ProviderStatus cancel(String providerPaymentId) {
        return paid.contains(providerPaymentId) ? ProviderStatus.SUCCEEDED : ProviderStatus.CANCELED;
    }

    //Имитация оплаты того, что товар оплачен, на проде этим будет заниматься провайдер
    public void markPaid(String providerPaymentId) {
        paid.add(providerPaymentId);
    }
}

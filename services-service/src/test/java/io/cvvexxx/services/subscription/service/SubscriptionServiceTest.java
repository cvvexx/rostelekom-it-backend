package io.cvvexxx.services.subscription.service;

import io.cvvexxx.services.client.BillingServiceRestClient;
import io.cvvexxx.services.client.ChargeOutcome;
import io.cvvexxx.services.subscription.dto.SubscriptionDto;
import io.cvvexxx.services.subscription.entity.Subscription;
import io.cvvexxx.services.subscription.entity.SubscriptionStatus;
import io.cvvexxx.services.subscription.exception.ChargeDeclinedException;
import io.cvvexxx.services.subscription.exception.SubscriptionAlreadyCanceledException;
import io.cvvexxx.services.subscription.exception.SubscriptionAlreadyExistsException;
import io.cvvexxx.services.subscription.exception.TariffInactiveException;
import io.cvvexxx.services.subscription.repository.SubscriptionRepository;
import io.cvvexxx.services.tariff.entity.BillingPeriod;
import io.cvvexxx.services.tariff.entity.Tariff;
import io.cvvexxx.services.tariff.entity.TariffCategory;
import io.cvvexxx.services.tariff.repository.TariffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private TariffRepository tariffRepository;
    @Mock
    private BillingServiceRestClient billingClient;
    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private final UUID userId = UUID.randomUUID();
    private final UUID tariffId = UUID.randomUUID();
    private Tariff activeTariff;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(subscriptionService, "leaseTtl", 300000L);
        activeTariff = Tariff.builder()
                .id(tariffId)
                .name("Интернет 500")
                .category(TariffCategory.INTERNET)
                .price(new BigDecimal("500.00"))
                .currency("RUB")
                .billingPeriod(BillingPeriod.MONTH)
                .active(true)
                .build();

        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });
        lenient().doAnswer(invocation -> {
            Consumer< org.springframework.transaction.TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(new SimpleTransactionStatus());
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    @DisplayName("Успешная подписка: деньги списаны, транзакция сохранена, lease снят")
    void subscribe_success() {
        when(tariffRepository.findById(tariffId)).thenReturn(Optional.of(activeTariff));
        when(subscriptionRepository.insertActiveIfAbsent(any(), eq(userId), eq(tariffId), any(), any(), any(), any(), any()))
                .thenReturn(1);

        UUID txId = UUID.randomUUID();
        when(billingClient.createCharge(any())).thenReturn(new ChargeOutcome.Charged(txId));
        Subscription lockedSub = Subscription.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tariff(activeTariff)
                .status(SubscriptionStatus.ACTIVE)
                .price(activeTariff.getPrice())
                .currency("RUB")
                .build();
        when(subscriptionRepository.findByIdForUpdate(any())).thenReturn(Optional.of(lockedSub));

        SubscriptionDto result = subscriptionService.subscribe(userId, tariffId);

        assertThat(result.lastChargeTransactionId()).isEqualTo(txId);
        assertThat(result.tariffName()).isEqualTo("Интернет 500");
        assertThat(lockedSub.getChargeLockedUntil()).isNull();
        verify(billingClient).createCharge(any());
    }

    @Test
    @DisplayName("Ошибка: подключение архивного тарифа бросает TariffInactiveException")
    void subscribe_tariffInactive() {
        activeTariff.setActive(false);
        when(tariffRepository.findById(tariffId)).thenReturn(Optional.of(activeTariff));

        assertThatThrownBy(() -> subscriptionService.subscribe(userId, tariffId))
                .isInstanceOf(TariffInactiveException.class);

        verifyNoInteractions(subscriptionRepository, billingClient);
    }

    @Test
    @DisplayName("Ошибка: повторное подключение активного тарифа бросает SubscriptionAlreadyExistsException")
    void subscribe_alreadyExists() {
        when(tariffRepository.findById(tariffId)).thenReturn(Optional.of(activeTariff));
        when(subscriptionRepository.insertActiveIfAbsent(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);

        assertThatThrownBy(() -> subscriptionService.subscribe(userId, tariffId))
                .isInstanceOf(SubscriptionAlreadyExistsException.class);

        verifyNoInteractions(billingClient);
    }

    @Test
    @DisplayName("Отказ биллинга: черновик подписки удаляется и бросается ChargeDeclinedException")
    void subscribe_billingDeclined_deletesDraft() {
        when(tariffRepository.findById(tariffId)).thenReturn(Optional.of(activeTariff));
        when(subscriptionRepository.insertActiveIfAbsent(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(billingClient.createCharge(any())).thenReturn(new ChargeOutcome.Declined("charge.insufficient-funds"));

        assertThatThrownBy(() -> subscriptionService.subscribe(userId, tariffId))
                .isInstanceOf(ChargeDeclinedException.class)
                .hasMessage("charge.insufficient-funds");

        verify(subscriptionRepository).deleteById(any());
    }

    @Test
    @DisplayName("Отмена подписки: при immediate=false ставит флаг cancelAtPeriodEnd=true и оставляет ACTIVE")
    void cancelSubscription_atPeriodEnd() {
        UUID subId = UUID.randomUUID();
        Subscription sub = Subscription.builder()
                .id(subId)
                .userId(userId)
                .tariff(activeTariff)
                .status(SubscriptionStatus.ACTIVE)
                .price(activeTariff.getPrice())
                .build();
        when(subscriptionRepository.findByIdAndUserIdForUpdate(subId, userId)).thenReturn(Optional.of(sub));

        SubscriptionDto result = subscriptionService.cancelSubscription(userId, subId, false, false);

        assertThat(result.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(result.cancelAtPeriodEnd()).isTrue();
    }

    @Test
    @DisplayName("Отмена уже отменённой подписки бросает SubscriptionAlreadyCanceledException")
    void cancelSubscription_alreadyCanceled_throwsException() {
        UUID subId = UUID.randomUUID();
        Subscription sub = Subscription.builder()
                .id(subId)
                .userId(userId)
                .tariff(activeTariff)
                .status(SubscriptionStatus.CANCELED)
                .build();
        when(subscriptionRepository.findByIdAndUserIdForUpdate(subId, userId)).thenReturn(Optional.of(sub));
        assertThatThrownBy(() -> subscriptionService.cancelSubscription(userId, subId, true, false))
                .isInstanceOf(SubscriptionAlreadyCanceledException.class);
    }
}
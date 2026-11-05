package io.cvvexxx.services.subscription.service;

import io.cvvexxx.services.client.BillingServiceRestClient;
import io.cvvexxx.services.client.ChargeOutcome;
import io.cvvexxx.services.subscription.dto.CreateChargeDto;
import io.cvvexxx.services.subscription.dto.SubscriptionDto;
import io.cvvexxx.services.subscription.entity.Subscription;
import io.cvvexxx.services.subscription.entity.SubscriptionStatus;
import io.cvvexxx.services.subscription.exception.ChargeDeclinedException;
import io.cvvexxx.services.subscription.exception.SubscriptionAlreadyCanceledException;
import io.cvvexxx.services.subscription.exception.SubscriptionAlreadyExistsException;
import io.cvvexxx.services.subscription.exception.SubscriptionNotFoundException;
import io.cvvexxx.services.subscription.exception.TariffInactiveException;
import io.cvvexxx.services.subscription.repository.SubscriptionRepository;
import io.cvvexxx.services.tariff.entity.BillingPeriod;
import io.cvvexxx.services.tariff.entity.Tariff;
import io.cvvexxx.services.tariff.entity.TariffCategory;
import io.cvvexxx.services.tariff.exception.TariffNotFoundException;
import io.cvvexxx.services.tariff.repository.TariffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
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

    @Test
    @DisplayName("Ошибка: несуществующий тариф бросает TariffNotFoundException")
    void subscribe_tariffNotFound() {
        when(tariffRepository.findById(tariffId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.subscribe(userId, tariffId))
                .isInstanceOf(TariffNotFoundException.class);

        verifyNoInteractions(subscriptionRepository, billingClient);
    }

    @Test
    @DisplayName("Биллинг недоступен: подписка переводится в SUSPENDED без lease, исключение пробрасывается")
    void subscribe_billingError_suspendsSubscription() {
        when(tariffRepository.findById(tariffId)).thenReturn(Optional.of(activeTariff));
        when(subscriptionRepository.insertActiveIfAbsent(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(billingClient.createCharge(any())).thenThrow(new IllegalStateException("billing down"));
        Subscription draft = Subscription.builder()
                .tariff(activeTariff)
                .status(SubscriptionStatus.ACTIVE)
                .chargeLockedUntil(Instant.now().plusSeconds(300))
                .build();
        when(subscriptionRepository.findByIdForUpdate(any())).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> subscriptionService.subscribe(userId, tariffId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("billing down");

        assertThat(draft.getStatus()).isEqualTo(SubscriptionStatus.SUSPENDED);
        assertThat(draft.getChargeLockedUntil()).isNull();
        assertThat(draft.getNextChargeAt()).isNotNull();
        verify(subscriptionRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Счёт в биллинге закрыт: черновик удаляется, бросается ChargeDeclinedException(charge.account-closed)")
    void subscribe_accountClosed_deletesDraft() {
        when(tariffRepository.findById(tariffId)).thenReturn(Optional.of(activeTariff));
        when(subscriptionRepository.insertActiveIfAbsent(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(billingClient.createCharge(any())).thenReturn(new ChargeOutcome.AccountClosed());

        assertThatThrownBy(() -> subscriptionService.subscribe(userId, tariffId))
                .isInstanceOf(ChargeDeclinedException.class)
                .hasMessage("charge.account-closed");

        verify(subscriptionRepository).deleteById(any());
    }

    @Test
    @DisplayName("В биллинг уходит сумма тарифа, userId и ключ идемпотентности с id подписки")
    void subscribe_sendsCorrectChargeRequest() {
        when(tariffRepository.findById(tariffId)).thenReturn(Optional.of(activeTariff));
        ArgumentCaptor<UUID> subIdCaptor = ArgumentCaptor.forClass(UUID.class);
        when(subscriptionRepository.insertActiveIfAbsent(subIdCaptor.capture(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(billingClient.createCharge(any())).thenReturn(new ChargeOutcome.Declined("charge.insufficient-funds"));

        assertThatThrownBy(() -> subscriptionService.subscribe(userId, tariffId));

        ArgumentCaptor<CreateChargeDto> chargeCaptor = ArgumentCaptor.forClass(CreateChargeDto.class);
        verify(billingClient).createCharge(chargeCaptor.capture());
        CreateChargeDto charge = chargeCaptor.getValue();
        assertThat(charge.userId()).isEqualTo(userId);
        assertThat(charge.amount()).isEqualByComparingTo("500.00");
        assertThat(charge.idempotencyKey()).startsWith(subIdCaptor.getValue() + ":");
        verify(subscriptionRepository).deleteById(subIdCaptor.getValue());
    }

    @Test
    @DisplayName("Немедленная отмена: CANCELED, canceledAt проставлен, lease снят")
    void cancelSubscription_immediate() {
        UUID subId = UUID.randomUUID();
        Subscription sub = Subscription.builder()
                .id(subId)
                .userId(userId)
                .tariff(activeTariff)
                .status(SubscriptionStatus.ACTIVE)
                .cancelAtPeriodEnd(true)
                .chargeLockedUntil(Instant.now().plusSeconds(300))
                .build();
        when(subscriptionRepository.findByIdAndUserIdForUpdate(subId, userId)).thenReturn(Optional.of(sub));

        SubscriptionDto result = subscriptionService.cancelSubscription(userId, subId, true, false);

        assertThat(result.status()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(result.canceledAt()).isNotNull();
        assertThat(result.cancelAtPeriodEnd()).isFalse();
        assertThat(sub.getChargeLockedUntil()).isNull();
    }

    @Test
    @DisplayName("Отмена SUSPENDED подписки при immediate=false всё равно отменяет её сразу")
    void cancelSubscription_suspended_cancelsImmediately() {
        UUID subId = UUID.randomUUID();
        Subscription sub = Subscription.builder()
                .id(subId)
                .userId(userId)
                .tariff(activeTariff)
                .status(SubscriptionStatus.SUSPENDED)
                .build();
        when(subscriptionRepository.findByIdAndUserIdForUpdate(subId, userId)).thenReturn(Optional.of(sub));

        SubscriptionDto result = subscriptionService.cancelSubscription(userId, subId, false, false);

        assertThat(result.status()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(result.canceledAt()).isNotNull();
    }

    @Test
    @DisplayName("Админ отменяет чужую подписку: поиск без фильтра по userId")
    void cancelSubscription_asAdmin_ignoresOwner() {
        UUID subId = UUID.randomUUID();
        Subscription sub = Subscription.builder()
                .id(subId)
                .userId(UUID.randomUUID())
                .tariff(activeTariff)
                .status(SubscriptionStatus.ACTIVE)
                .build();
        when(subscriptionRepository.findByIdForUpdate(subId)).thenReturn(Optional.of(sub));

        subscriptionService.cancelSubscription(userId, subId, true, true);

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        verify(subscriptionRepository, never()).findByIdAndUserIdForUpdate(any(), any());
    }

    @Test
    @DisplayName("Отмена чужой/несуществующей подписки пользователем бросает SubscriptionNotFoundException")
    void cancelSubscription_notOwner_throwsNotFound() {
        UUID subId = UUID.randomUUID();
        when(subscriptionRepository.findByIdAndUserIdForUpdate(subId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.cancelSubscription(userId, subId, false, false))
                .isInstanceOf(SubscriptionNotFoundException.class);
    }

    @Test
    @DisplayName("Пользователь видит только свою подписку, админ ищет по id без userId")
    void getSubscription_userVsAdmin() {
        UUID subId = UUID.randomUUID();
        Subscription sub = Subscription.builder()
                .id(subId)
                .userId(userId)
                .tariff(activeTariff)
                .status(SubscriptionStatus.ACTIVE)
                .build();
        when(subscriptionRepository.findByIdAndUserId(subId, userId)).thenReturn(Optional.of(sub));
        when(subscriptionRepository.findWithTariffById(subId)).thenReturn(Optional.of(sub));

        assertThat(subscriptionService.getSubscription(userId, subId, false).id()).isEqualTo(subId);
        assertThat(subscriptionService.getSubscription(UUID.randomUUID(), subId, true).id()).isEqualTo(subId);

        verify(subscriptionRepository).findByIdAndUserId(subId, userId);
        verify(subscriptionRepository).findWithTariffById(subId);
    }

    @Test
    @DisplayName("Чужая подписка для пользователя не находится: SubscriptionNotFoundException")
    void getSubscription_notOwner_throwsNotFound() {
        UUID subId = UUID.randomUUID();
        when(subscriptionRepository.findByIdAndUserId(subId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.getSubscription(userId, subId, false))
                .isInstanceOf(SubscriptionNotFoundException.class);
    }
}
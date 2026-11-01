package io.cvvexxx.services.subscription.service;

import io.cvvexxx.services.subscription.entity.Subscription;
import io.cvvexxx.services.subscription.entity.SubscriptionStatus;
import io.cvvexxx.services.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

//Записывает результат списания после ответа billing. Каждый метод - короткая транзакция без HTTP внутри,
//save не нужен, Hibernate сам сохранит изменения при коммите.
//false - подписку уже продлили или отменили (запоздавший ответ billing после истечения lease), ничего не меняем
@Service
@RequiredArgsConstructor
public class SubscriptionChargeService {

    private final SubscriptionRepository subscriptionRepository;

    //billing списал деньги: продлеваем период и снимаем lease
    @Transactional
    public boolean markCharged(UUID id, Instant claimedNextChargeAt, Instant periodStart, Instant nextChargeAt,
                               UUID transactionId) {
        return findClaimed(id, claimedNextChargeAt).map(subscription -> {
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setCurrentPeriodStart(periodStart);
            subscription.setNextChargeAt(nextChargeAt);
            subscription.setLastChargeTransactionId(transactionId);
            subscription.setChargeLockedUntil(null);
            return true;
        }).orElse(false);
    }

    //billing отказал (нет денег, счёт заблокирован, нет счёта): приостанавливаем до retryAt
    @Transactional
    public boolean markSuspended(UUID id, Instant claimedNextChargeAt, Instant retryAt) {
        return findClaimed(id, claimedNextChargeAt).map(subscription -> {
            subscription.setStatus(SubscriptionStatus.SUSPENDED);
            subscription.setChargeLockedUntil(retryAt);
            return true;
        }).orElse(false);
    }

    //Отмена в конце периода или закрытый счёт в billing
    @Transactional
    public boolean markCanceled(UUID id, Instant claimedNextChargeAt, Instant now) {
        return findClaimed(id, claimedNextChargeAt).map(subscription -> {
            subscription.setStatus(SubscriptionStatus.CANCELED);
            subscription.setCanceledAt(now);
            subscription.setChargeLockedUntil(null);
            return true;
        }).orElse(false);
    }

    //Подписка всё ещё та, что взяли в claim: не отменена и next_charge_at не сдвинули
    private Optional<Subscription> findClaimed(UUID id, Instant claimedNextChargeAt) {
        return subscriptionRepository.findByIdForUpdate(id)
                .filter(subscription -> subscription.getStatus() != SubscriptionStatus.CANCELED
                        && subscription.getNextChargeAt().equals(claimedNextChargeAt));
    }
}

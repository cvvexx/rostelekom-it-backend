package io.cvvexxx.services.subscription.job;

import io.cvvexxx.services.client.BillingServiceRestClient;
import io.cvvexxx.services.client.ChargeOutcome;
import io.cvvexxx.services.subscription.dto.CreateChargeDto;
import io.cvvexxx.services.subscription.entity.SubscriptionStatus;
import io.cvvexxx.services.subscription.repository.SubscriptionRepository;
import io.cvvexxx.services.subscription.repository.SubscriptionRepository.DueSubscription;
import io.cvvexxx.services.subscription.service.SubscriptionChargeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionChargingJob {
    private static final int BATCH_SIZE = 100;

    @Value("${app.subscription.lease-ttl:2000}")
    private long leaseTtl;

    //Через сколько повторить списание после отказа billing
    @Value("${app.subscription.charge-retry-delay:PT1H}")
    private Duration retryDelay;

    private final BillingServiceRestClient restClient;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionChargeService subscriptionChargeService;


    @Scheduled(fixedDelayString = "${app.subscription.charge-interval:PT1M}")
    public void chargeDueSubscriptions() {
        List<DueSubscription> batch;
        do {
            Instant now = Instant.now();
            //Берем подписки которые скоро нужно оплатить
            batch = subscriptionRepository.claimDueForCharge(now, now.plus(Duration.ofMillis(leaseTtl)), BATCH_SIZE);
            for (DueSubscription s : batch) {
                try {
                    //По одной пытаемся обработать
                    process(s, now);
                } catch (Exception e) {
                    log.error("Не удалось списать по подписке {}", s.getId(), e);
                }
            }
        } while (batch.size() == BATCH_SIZE);
    }


    private void process(DueSubscription subscription, Instant now) {
        UUID id = subscription.getId();
        Instant claimedNextChargeAt = subscription.getNextChargeAt();

        if (subscription.getCancelAtPeriodEnd()) {
            subscriptionChargeService.markCanceled(id, claimedNextChargeAt, now);
            return;
        }

        ChargeOutcome outcome = restClient.createCharge(new CreateChargeDto(
                subscription.getUserId(),
                subscription.getPrice(),
                idempotencyKey(subscription),
                "Оплата тарифа " + subscription.getTariffName()
        ));

        boolean applied = switch (outcome) {
            case ChargeOutcome.Charged charged -> {
                //Приостановленную подписку оплатили только сейчас: новый период считаем от оплаты, а не от пропущенной даты
                Instant periodStart = subscription.getStatus() == SubscriptionStatus.SUSPENDED ? now : claimedNextChargeAt;
                //TODO(исправить если появятся подписки у которых оплата не раз в месяц)
                Instant nextChargeAt = periodStart.atOffset(ZoneOffset.UTC).plusMonths(1).toInstant();
                yield subscriptionChargeService.markCharged(id, claimedNextChargeAt, periodStart, nextChargeAt,
                        charged.transactionId());
            }
            //Не хватило денег, аккаунт не найден/заблокирован
            case ChargeOutcome.Declined declined -> {
                log.info("billing отказал в списании по подписке {}: {}", id, declined.code());
                //останавливаем обслуживание подписки
                yield subscriptionChargeService.markSuspended(id, claimedNextChargeAt, now.plus(retryDelay));
            }
            //Аккаунт закрыт -> отменяем подписку
            case ChargeOutcome.AccountClosed ignored ->
                    subscriptionChargeService.markCanceled(id, claimedNextChargeAt, now);
        };

        if (!applied) {
            log.debug("Подписку {} уже обработали или отменили, результат списания не применён", id);
        }
    }


    private static String idempotencyKey(DueSubscription subscription) {
        return subscription.getId() + ":" + subscription.getNextChargeAt().getEpochSecond();
    }
}

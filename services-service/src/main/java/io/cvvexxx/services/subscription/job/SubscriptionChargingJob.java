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

    //Интервал ретраев после отказа billing растёт вместе с просрочкой: от min до max
    @Value("${app.subscription.charge-retry-min-delay:PT1H}")
    private Duration minRetryDelay;

    @Value("${app.subscription.charge-retry-max-delay:PT24H}")
    private Duration maxRetryDelay;

    //Сколько приостановленная подписка ждёт оплату, потом отменяем её и больше не ходим в billing
    @Value("${app.subscription.grace-period:P30D}")
    private Duration gracePeriod;

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

        //Проверка сколько уже не оплачена подписка(now - nextChargeAt), если больше gracePeriod то отменяем
        //Чтобы не ходить в billing service и не нагружать его просто так
        //TODO(В будущем добавить эндпоинт/кафку чтобы при пополнении баланса сервис услуг пытался списать деньги)
        Duration overdue = Duration.between(claimedNextChargeAt, now);
        if (subscription.getStatus() == SubscriptionStatus.SUSPENDED && overdue.compareTo(gracePeriod) > 0) {
            log.info("Подписка {} не оплачена дольше {}, отменяем", id, gracePeriod);
            subscriptionChargeService.markCanceled(id, claimedNextChargeAt, now);
            return;
        }

        ChargeOutcome outcome = restClient.createCharge(new CreateChargeDto(
                subscription.getUserId(),
                subscription.getPrice(),
                idempotencyKey(subscription),
                "Оплата тарифа " + subscription.getTariffName()
        ));

        switch (outcome) {
            case ChargeOutcome.Charged charged -> {
                //Приостановленную подписку оплатили только сейчас: новый период считаем от оплаты, а не от пропущенной даты
                Instant periodStart = subscription.getStatus() == SubscriptionStatus.SUSPENDED ? now : claimedNextChargeAt;
                //TODO(исправить если появятся подписки у которых оплата не раз в месяц)
                Instant nextChargeAt = periodStart.atOffset(ZoneOffset.UTC).plusMonths(1).toInstant();
                if (
                        !subscriptionChargeService.markCharged(id, claimedNextChargeAt, periodStart, nextChargeAt,
                                charged.transactionId())
                ) {
                    //подписку могли отменить пока мы пытались списать деньги, по факту пользователь
                    //заплатил за период отмененной подписки
                    //TODO(сделать возврат через billing, когда появится refund для charge)
                    log.warn("Списали {} по подписке {}, но результат не применён: подписку отменили или уже продлили. Транзакция {}",
                            subscription.getPrice(), id, charged.transactionId());
                }
            }
            //Не хватило денег, аккаунт не найден/заблокирован
            case ChargeOutcome.Declined declined -> {
                log.info("billing отказал в списании по подписке {}: {}", id, declined.code());
                //Растущий delay чтобы часто не нагружать billing service
                Duration retryDelay = Duration.ofSeconds(
                        Math.clamp(overdue.toSeconds(), minRetryDelay.toSeconds(), maxRetryDelay.toSeconds()));
                subscriptionChargeService.markSuspended(id, claimedNextChargeAt, now.plus(retryDelay));
            }
            //Аккаунт закрыт -> отменяем подписку
            case ChargeOutcome.AccountClosed ignored ->
                    subscriptionChargeService.markCanceled(id, claimedNextChargeAt, now);
        }
    }


    private static String idempotencyKey(DueSubscription subscription) {
        return subscription.getId() + ":" + subscription.getNextChargeAt().getEpochSecond();
    }
}

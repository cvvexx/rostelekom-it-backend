package io.cvvexxx.services.subscription.job;

import io.cvvexxx.services.subscription.dto.ToppedUpEvent;
import io.cvvexxx.services.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class BillingEventsListener {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionChargingJob subscriptionChargingJob;

    @KafkaListener(
            topics = "${app.kafka.topics.account-topped-up}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onAccountToppedUp(ToppedUpEvent event) {
        log.info("Получено событие пополнения баланса: userId={}, amount={}",
                event.userId(), event.amount());

        int unlocked = subscriptionRepository.unlockSuspendedForUser(event.userId(), Instant.now());
        if (unlocked > 0) {
            subscriptionChargingJob.chargeDueSubscriptions();
        }
    }
}
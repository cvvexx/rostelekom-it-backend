package io.cvvexxx.billings.payment.service;

import io.cvvexxx.billings.payment.dto.ToppedUpEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
@RequiredArgsConstructor
public class BillingKafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.account-topped-up}")
    private String topic;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountToppedUpEvent(ToppedUpEvent event) {
        kafkaTemplate.send(topic, event.userId().toString(), event)
                .whenComplete((result, e) -> {
                    if (e != null) {
                        log.error("Не удалось отправить событие пополнения баланса для userId={}", event.userId(), e);
                    } else {
                        log.info("Событие пополнения баланса отправлено в Kafka: userId={}, amount={}",
                                event.userId(), event.amount());
                    }
                });
    }
}
package io.cvvexxx.services.subscription.job;

import io.cvvexxx.services.config.KafkaConfig;
import io.cvvexxx.services.subscription.dto.ToppedUpEvent;
import io.cvvexxx.services.subscription.repository.SubscriptionRepository;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = {KafkaConfig.class, BillingEventsListener.class},
        properties = {
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "spring.kafka.consumer.group-id=test-services-group",
                "app.kafka.topics.account-topped-up=billing.account.topped-up"
        }
)
@EmbeddedKafka(partitions = 1, topics = {"billing.account.topped-up"})
class BillingKafkaIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Value("${app.kafka.topics.account-topped-up}")
    private String topic;

    @MockitoBean
    private SubscriptionRepository subscriptionRepository;

    @MockitoBean
    private SubscriptionChargingJob subscriptionChargingJob;

    @Test
    void shouldConsumeJsonMessageFromKafkaAndTriggerCharging() {
        UUID userId = UUID.randomUUID();

        // Имитируем, что в базе нашлась 1 просроченная подписка для разблокировки
        when(subscriptionRepository.unlockSuspendedForUser(eq(userId), any(Instant.class)))
                .thenReturn(1);

        // Настраиваем тестовый продюсер для отправки JSON-сообщения
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaBroker.getBrokersAsString());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
        producerProps.put(JacksonJsonSerializer.ADD_TYPE_INFO_HEADERS, false);

        var producerFactory = new DefaultKafkaProducerFactory<String, Object>(producerProps);
        var kafkaTemplate = new KafkaTemplate<>(producerFactory);

        var event = new ToppedUpEvent(
                userId,
                UUID.randomUUID(),
                new BigDecimal("500.00"),
                Instant.now()
        );

        // Отправляем событие в локальный встроенный брокер Kafka
        kafkaTemplate.send(topic, userId.toString(), event);

        // Проверяем, что @KafkaListener перехватил сообщение, вызвал репозиторий и запустил джобу списания
        verify(subscriptionRepository, timeout(5000).times(1)).unlockSuspendedForUser(eq(userId), any(Instant.class));
        verify(subscriptionChargingJob, timeout(5000).times(1)).chargeDueSubscriptions();
    }
}
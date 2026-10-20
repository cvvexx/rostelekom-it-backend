package io.cvvexxx.billings.payment.repository;

import io.cvvexxx.billings.payment.entity.Payment;
import io.cvvexxx.billings.payment.entity.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@EnabledIfDockerAvailable
class PaymentRepositoryIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("findByAccountIdAndIdempotencyKey: находит платёж только своего счёта")
    void findByIdempotencyKey_isScopedToAccount() {
        UUID accountId = insertAccount();
        UUID otherAccountId = insertAccount();
        UUID paymentId = insertPayment(accountId, "key-1", "PENDING", null, OffsetDateTime.now());

        assertThat(paymentRepository.findByAccountIdAndIdempotencyKey(accountId, "key-1"))
                .map(Payment::getId).contains(paymentId);
        assertThat(paymentRepository.findByAccountIdAndIdempotencyKey(otherAccountId, "key-1")).isEmpty();
        assertThat(paymentRepository.findByAccountIdAndIdempotencyKey(accountId, "key-2")).isEmpty();
    }

    @Test
    @DisplayName("findByIdAndAccountId: чужой платёж по id не отдаётся")
    void findByIdAndAccountId_doesNotLeakForeignPayment() {
        UUID accountId = insertAccount();
        UUID otherAccountId = insertAccount();
        UUID paymentId = insertPayment(accountId, "key-1", "PENDING", null, OffsetDateTime.now());

        assertThat(paymentRepository.findByIdAndAccountId(paymentId, accountId)).isPresent();
        assertThat(paymentRepository.findByIdAndAccountId(paymentId, otherAccountId)).isEmpty();
    }

    @Test
    @DisplayName("findByProviderPaymentIdForUpdate: ищет по паре провайдер + id у провайдера")
    void findByProviderPaymentIdForUpdate_matchesProviderAndExternalId() {
        UUID accountId = insertAccount();
        String externalId = UUID.randomUUID().toString();
        UUID paymentId = insertPayment(accountId, "key-1", "PENDING", externalId, OffsetDateTime.now());

        Optional<UUID> found = transactionTemplate.execute(status -> paymentRepository
                .findByProviderPaymentIdForUpdate("TEST", externalId).map(Payment::getId));
        Optional<UUID> otherProvider = transactionTemplate.execute(status -> paymentRepository
                .findByProviderPaymentIdForUpdate("OTHER", externalId).map(Payment::getId));

        assertThat(found).contains(paymentId);
        assertThat(otherProvider).isEmpty();
    }

    @Test
    @DisplayName("findByStatusAndCreatedAtBefore: возвращает только зависшие платежи нужного статуса")
    void findByStatusAndCreatedAtBefore_returnsStalePayments() {
        UUID accountId = insertAccount();
        OffsetDateTime now = OffsetDateTime.now();
        UUID stale = insertPayment(accountId, "stale", "PENDING", null, now.minusHours(2));
        UUID fresh = insertPayment(accountId, "fresh", "PENDING", null, now);
        UUID staleButDone = insertPayment(accountId, "done", "SUCCEEDED", null, now.minusHours(2));

        Instant threshold = Instant.now().minus(1, ChronoUnit.HOURS);

        assertThat(paymentRepository.findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, threshold))
                .extracting(Payment::getId)
                .contains(stale)
                .doesNotContain(fresh, staleButDone);
    }

    private UUID insertAccount() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into accounts (id, user_id, number) values (?, ?, nextval('account_number_seq')::text)",
                id, UUID.randomUUID());
        return id;
    }

    private UUID insertPayment(UUID accountId, String idempotencyKey, String status, String providerPaymentId,
                               OffsetDateTime createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into payments (id, account_id, amount, status, provider, provider_payment_id,
                                      idempotency_key, created_at)
                values (?, ?, 100.00, ?, 'TEST', ?, ?, ?)
                """, id, accountId, status, providerPaymentId, idempotencyKey, createdAt);
        return id;
    }
}

package io.cvvexxx.billings.payment.job;

import io.cvvexxx.billings.account.service.AccountService;
import io.cvvexxx.billings.payment.provider.stub.StubPaymentProvider;
import io.cvvexxx.billings.payment.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@EnabledIfDockerAvailable
class PendingPaymentsCancellingJobIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private PendingPaymentsCancellingJob job;
    @Autowired
    private AccountService accountService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private StubPaymentProvider stubPaymentProvider;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Неоплаченный платёж старше pending-ttl отменяется, свежий не трогаем")
    void cancelsOnlyStalePayments() {
        UUID userId = UUID.randomUUID();
        UUID accountId = accountService.openAccount(userId).account().id();
        UUID stale = createPayment(userId);
        UUID fresh = createPayment(userId);
        setCreatedAt(stale, OffsetDateTime.now().minusMinutes(21));

        job.cancelPendingPayments();

        assertThat(status(stale)).isEqualTo("CANCELED");
        assertThat(status(fresh)).isEqualTo("PENDING");
        assertThat(balance(accountId)).isZero();
    }

    @Test
    @DisplayName("Зависший платёж уже оплачен у провайдера (webhook потерялся): зачисляем, а не отменяем")
    void creditsStalePaymentPaidAtProvider() {
        UUID userId = UUID.randomUUID();
        UUID accountId = accountService.openAccount(userId).account().id();
        UUID paymentId = createPayment(userId);
        setCreatedAt(paymentId, OffsetDateTime.now().minusHours(1));
        stubPaymentProvider.markPaid("stub-" + paymentId);

        job.cancelPendingPayments();

        assertThat(status(paymentId)).isEqualTo("SUCCEEDED");
        assertThat(balance(accountId)).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("Зависших платежей больше одной пачки: за один запуск обрабатываются все")
    void processesAllBatches() {
        UUID accountId = accountService.openAccount(UUID.randomUUID()).account().id();
        OffsetDateTime createdAt = OffsetDateTime.now().minusDays(1);
        for (int i = 0; i < 250; i++) {//Платёж без provider_payment_id: у провайдера не создан, отменяется только у нас
            jdbc.update("""
                    insert into payments (id, account_id, amount, status, provider, idempotency_key, created_at)
                    values (?, ?, 10.00, 'PENDING', 'STUB', ?, ?)
                    """, UUID.randomUUID(), accountId, UUID.randomUUID().toString(), createdAt.plusSeconds(i));
        }

        job.cancelPendingPayments();

        assertThat(jdbc.queryForObject(
                "select count(*) from payments where account_id = ? and status = 'PENDING'", Integer.class, accountId))
                .isZero();
    }

    private UUID createPayment(UUID userId) {
        return paymentService.createPayment(userId, new BigDecimal("10.00"), UUID.randomUUID().toString()).id();
    }

    private void setCreatedAt(UUID paymentId, OffsetDateTime createdAt) {
        jdbc.update("update payments set created_at = ? where id = ?", createdAt, paymentId);
    }

    private String status(UUID paymentId) {
        return jdbc.queryForObject("select status from payments where id = ?", String.class, paymentId);
    }

    private BigDecimal balance(UUID accountId) {
        return jdbc.queryForObject("select balance from accounts where id = ?", BigDecimal.class, accountId);
    }
}

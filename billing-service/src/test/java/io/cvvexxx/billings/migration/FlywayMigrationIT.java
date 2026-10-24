package io.cvvexxx.billings.migration;

import io.cvvexxx.billings.account.entity.AccountStatus;
import io.cvvexxx.billings.payment.entity.PaymentStatus;
import io.cvvexxx.billings.transaction.entity.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@EnabledIfDockerAvailable
class FlywayMigrationIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @ParameterizedTest
    @EnumSource(AccountStatus.class)
    @DisplayName("Каждый статус счёта из enum проходит check-constraint")
    void everyAccountStatusPassesCheckConstraint(AccountStatus status) {
        assertThatCode(() -> insertAccount(status.name())).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(TransactionType.class)
    @DisplayName("Каждый тип транзакции из enum проходит check-constraint")
    void everyTransactionTypePassesCheckConstraint(TransactionType type) {
        UUID accountId = insertAccount("ACTIVE");

        assertThatCode(() -> insertTransaction(accountId, type.name(), "10.00", null))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    @DisplayName("Каждый статус платежа из enum проходит check-constraint")
    void everyPaymentStatusPassesCheckConstraint(PaymentStatus status) {
        UUID accountId = insertAccount("ACTIVE");

        assertThatCode(() -> insertPayment(accountId, UUID.randomUUID().toString(), status.name(), "100.00"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Ключ идемпотентности уникален в рамках счёта, но может повторяться у разных счетов")
    void idempotencyKeyIsUniquePerAccount() {
        UUID accountId = insertAccount("ACTIVE");
        UUID otherAccountId = insertAccount("ACTIVE");
        insertPayment(accountId, "key-1", "PENDING", "100.00");

        assertThatThrownBy(() -> insertPayment(accountId, "key-1", "PENDING", "100.00"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_payments_account_id_idempotency_key");
        assertThatCode(() -> insertPayment(otherAccountId, "key-1", "PENDING", "100.00"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Один платёж не может зачислиться дважды, но по нему можно сделать возврат")
    void paymentCanBeToppedUpOnlyOnce() {
        UUID accountId = insertAccount("ACTIVE");
        UUID paymentId = insertPayment(accountId, UUID.randomUUID().toString(), "SUCCEEDED", "100.00");
        insertTransaction(accountId, "TOP_UP", "100.00", paymentId);

        assertThatThrownBy(() -> insertTransaction(accountId, "TOP_UP", "100.00", paymentId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_transactions_payment_id_top_up");
        assertThatCode(() -> insertTransaction(accountId, "REFUND", "100.00", paymentId))
                .doesNotThrowAnyException();
    }

    private UUID insertAccount(String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into accounts (id, user_id, number, status)
                values (?, ?, nextval('account_number_seq')::text, ?)
                """, id, UUID.randomUUID(), status);
        return id;
    }

    private void insertTransaction(UUID accountId, String type, String amount, UUID paymentId) {
        jdbc.update("""
                insert into transactions (id, account_id, type, amount, balance_after, payment_id)
                values (?, ?, ?, ?::numeric, 0, ?)
                """, UUID.randomUUID(), accountId, type, amount, paymentId);
    }

    private UUID insertPayment(UUID accountId, String idempotencyKey, String status, String amount) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into payments (id, account_id, amount, status, provider, idempotency_key)
                values (?, ?, ?::numeric, ?, 'TEST', ?)
                """, id, accountId, amount, status, idempotencyKey);
        return id;
    }
}

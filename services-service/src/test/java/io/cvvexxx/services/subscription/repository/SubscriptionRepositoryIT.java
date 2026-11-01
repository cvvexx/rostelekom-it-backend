package io.cvvexxx.services.subscription.repository;

import io.cvvexxx.services.subscription.entity.SubscriptionStatus;
import io.cvvexxx.services.subscription.repository.SubscriptionRepository.DueSubscription;
import io.cvvexxx.services.subscription.service.SubscriptionChargeService;
import org.junit.jupiter.api.BeforeEach;
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

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@EnabledIfDockerAvailable
class SubscriptionRepositoryIT {

    private static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");
    private static final Instant LOCKED_UNTIL = NOW.plus(Duration.ofMinutes(2));

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private SubscriptionRepository subscriptionRepository;
    @Autowired
    private SubscriptionChargeService subscriptionChargeService;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID tariffId;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from subscriptions");
        tariffId = jdbc.queryForObject("""
                insert into tariffs (id, name, category, price) values (gen_random_uuid(), 'Интернет 500', 'INTERNET', 499.00)
                returning id
                """, UUID.class);
    }

    @Test
    @DisplayName("claim берёт только подписки к списанию без живого lease и ставит lease, повторный claim пустой")
    void claimDueForCharge_takesOnlyDueAndUnlocked() {
        UUID dueActive = insertSubscription("ACTIVE", NOW.minusSeconds(60), null);
        UUID dueSuspended = insertSubscription("SUSPENDED", NOW.minusSeconds(60), null);
        UUID leaseExpired = insertSubscription("ACTIVE", NOW.minusSeconds(60), NOW.minusSeconds(1));
        insertSubscription("ACTIVE", NOW.plusSeconds(60), null);//ещё рано
        insertSubscription("CANCELED", NOW.minusSeconds(60), null);//отменена
        insertSubscription("ACTIVE", NOW.minusSeconds(60), NOW.plusSeconds(30));//lease другого инстанса

        List<DueSubscription> claimed = subscriptionRepository.claimDueForCharge(NOW, LOCKED_UNTIL, 100);

        assertThat(claimed).extracting(DueSubscription::getId)
                .containsExactlyInAnyOrder(dueActive, dueSuspended, leaseExpired);
        DueSubscription suspended = claimed.stream().filter(s -> s.getId().equals(dueSuspended)).findFirst().orElseThrow();
        assertThat(suspended.getStatus()).isEqualTo(SubscriptionStatus.SUSPENDED);
        assertThat(suspended.getNextChargeAt()).isEqualTo(NOW.minusSeconds(60));
        assertThat(suspended.getPrice()).isEqualByComparingTo("499.00");
        assertThat(suspended.getTariffName()).isEqualTo("Интернет 500");
        assertThat(suspended.getCancelAtPeriodEnd()).isFalse();
        assertThat(lockedUntil(dueActive)).isEqualTo(LOCKED_UNTIL);

        assertThat(subscriptionRepository.claimDueForCharge(NOW, LOCKED_UNTIL, 100)).isEmpty();
    }

    @Test
    @DisplayName("claim уважает batchSize и берёт самые старые первыми")
    void claimDueForCharge_respectsBatchSize() {
        UUID oldest = insertSubscription("ACTIVE", NOW.minusSeconds(300), null);
        insertSubscription("ACTIVE", NOW.minusSeconds(60), null);

        assertThat(subscriptionRepository.claimDueForCharge(NOW, LOCKED_UNTIL, 1))
                .extracting(DueSubscription::getId).containsExactly(oldest);
    }

    @Test
    @DisplayName("markCharged продлевает период и снимает lease, запоздавший повтор со старым next_charge_at ничего не меняет")
    void markCharged_ignoresStaleClaim() {
        Instant claimedNextChargeAt = NOW.minusSeconds(60);
        UUID id = insertSubscription("SUSPENDED", claimedNextChargeAt, LOCKED_UNTIL);
        UUID transactionId = UUID.randomUUID();
        Instant nextChargeAt = NOW.plus(Duration.ofDays(30));

        assertThat(subscriptionChargeService.markCharged(id, claimedNextChargeAt, NOW, nextChargeAt, transactionId)).isTrue();

        Map<String, Object> row = jdbc.queryForMap("select * from subscriptions where id = ?", id);
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(((Timestamp) row.get("current_period_start")).toInstant()).isEqualTo(NOW);
        assertThat(((Timestamp) row.get("next_charge_at")).toInstant()).isEqualTo(nextChargeAt);
        assertThat(row.get("last_charge_transaction_id")).isEqualTo(transactionId);
        assertThat(row.get("charge_locked_until")).isNull();

        assertThat(subscriptionChargeService.markCharged(id, claimedNextChargeAt, NOW, nextChargeAt, UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("markSuspended ставит SUSPENDED и backoff, markCanceled не трогает уже отменённую")
    void markSuspendedAndCanceled() {
        Instant claimedNextChargeAt = NOW.minusSeconds(60);
        UUID id = insertSubscription("ACTIVE", claimedNextChargeAt, LOCKED_UNTIL);
        Instant retryAt = NOW.plus(Duration.ofHours(1));

        assertThat(subscriptionChargeService.markSuspended(id, claimedNextChargeAt, retryAt)).isTrue();
        assertThat(jdbc.queryForObject("select status from subscriptions where id = ?", String.class, id)).isEqualTo("SUSPENDED");
        assertThat(lockedUntil(id)).isEqualTo(retryAt);

        assertThat(subscriptionChargeService.markCanceled(id, claimedNextChargeAt, NOW)).isTrue();
        assertThat(jdbc.queryForObject("select status from subscriptions where id = ?", String.class, id)).isEqualTo("CANCELED");
        assertThat(lockedUntil(id)).isNull();
        assertThat(subscriptionChargeService.markCanceled(id, claimedNextChargeAt, NOW)).isFalse();
    }

    private UUID insertSubscription(String status, Instant nextChargeAt, Instant chargeLockedUntil) {
        return jdbc.queryForObject("""
                insert into subscriptions (id, user_id, tariff_id, status, price, current_period_start, next_charge_at, charge_locked_until)
                values (gen_random_uuid(), ?, ?, ?, 499.00, ?, ?, ?)
                returning id
                """, UUID.class, UUID.randomUUID(), tariffId, status,
                Timestamp.from(nextChargeAt.minus(Duration.ofDays(30))), Timestamp.from(nextChargeAt),
                chargeLockedUntil == null ? null : Timestamp.from(chargeLockedUntil));
    }

    private Instant lockedUntil(UUID id) {
        Timestamp value = jdbc.queryForObject("select charge_locked_until from subscriptions where id = ?", Timestamp.class, id);
        return value == null ? null : value.toInstant();
    }
}

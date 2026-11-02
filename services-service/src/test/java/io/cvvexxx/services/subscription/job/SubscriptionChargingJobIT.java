package io.cvvexxx.services.subscription.job;

import io.cvvexxx.services.client.BillingServiceRestClient;
import io.cvvexxx.services.client.ChargeOutcome;
import io.cvvexxx.services.subscription.dto.CreateChargeDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpServerErrorException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

//Джоба берёт Instant.now() сама, поэтому next_charge_at задаём относительно реального времени,
//а моменты, которые она ставит от now, проверяем попаданием в окно [до запуска, после запуска]
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@EnabledIfDockerAvailable
class SubscriptionChargingJobIT {

    //Postgres хранит микросекунды, Instant.now() бывает точнее - даём запас на округление
    private static final Duration TOLERANCE = Duration.ofMillis(1);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockitoBean
    private BillingServiceRestClient billing;
    @Autowired
    private SubscriptionChargingJob job;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID tariffId;
    private Instant before;
    private Instant after;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from subscriptions");
        tariffId = jdbc.queryForObject("""
                insert into tariffs (id, name, category, price) values (gen_random_uuid(), 'Интернет 500', 'INTERNET', 499.00)
                returning id
                """, UUID.class);
    }

    @Test
    @DisplayName("Charged по ACTIVE: период продлевается от старой даты списания, в billing уходят верные данные")
    void charged_activeSubscription_extendsFromPreviousChargeDate() {
        Instant due = Instant.now().minus(Duration.ofMinutes(1)).truncatedTo(ChronoUnit.MILLIS);
        UUID id = insert("ACTIVE", due, false);
        UUID userId = userId(id);
        UUID transactionId = UUID.randomUUID();
        when(billing.createCharge(any())).thenReturn(new ChargeOutcome.Charged(transactionId));

        runJob();

        verify(billing).createCharge(new CreateChargeDto(
                userId, new BigDecimal("499.00"), id + ":" + due.getEpochSecond(), "Оплата тарифа Интернет 500"));
        Map<String, Object> row = row(id);
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(instant(row, "current_period_start")).isEqualTo(due);
        assertThat(instant(row, "next_charge_at")).isEqualTo(due.atOffset(ZoneOffset.UTC).plusMonths(1).toInstant());
        assertThat(row.get("last_charge_transaction_id")).isEqualTo(transactionId);
        assertThat(row.get("charge_locked_until")).isNull();
    }

    @Test
    @DisplayName("Charged по SUSPENDED: новый период считается от момента оплаты, подписка снова ACTIVE")
    void charged_suspendedSubscription_extendsFromNow() {
        UUID id = insert("SUSPENDED", Instant.now().minus(Duration.ofDays(3)), false);
        when(billing.createCharge(any())).thenReturn(new ChargeOutcome.Charged(UUID.randomUUID()));

        runJob();

        Map<String, Object> row = row(id);
        Instant periodStart = instant(row, "current_period_start");
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThatInWindow(periodStart, Duration.ZERO);
        assertThat(instant(row, "next_charge_at")).isEqualTo(periodStart.atOffset(ZoneOffset.UTC).plusMonths(1).toInstant());
    }

    @Test
    @DisplayName("Declined сразу после даты списания: SUSPENDED, ретрай через минимальный интервал (1 час)")
    void declined_freshOverdue_retriesAfterMinDelay() {
        Instant due = Instant.now().minus(Duration.ofMinutes(1));
        UUID id = insert("ACTIVE", due, false);
        when(billing.createCharge(any())).thenReturn(new ChargeOutcome.Declined("charge.insufficient-funds"));

        runJob();

        Map<String, Object> row = row(id);
        assertThat(row.get("status")).isEqualTo("SUSPENDED");
        assertThatInWindow(instant(row, "charge_locked_until"), Duration.ofHours(1));
        //Период не сдвигается: по нему считается просрочка и ключ идемпотентности
        assertThat(instant(row, "next_charge_at")).isCloseTo(due, within(1, ChronoUnit.MILLIS));
    }

    @Test
    @DisplayName("Declined при просрочке в 5 часов: ретрай через время просрочки")
    void declined_midOverdue_retriesAfterOverdue() {
        Instant due = Instant.now().minus(Duration.ofHours(5));
        UUID id = insert("SUSPENDED", due, false);
        when(billing.createCharge(any())).thenReturn(new ChargeOutcome.Declined("charge.insufficient-funds"));

        runJob();

        //retryAt = now + (now - due) с отброшенными долями секунды (джоба считает задержку в целых секундах),
        //окно [before, after] даёт нижнюю и верхнюю границы
        Instant lockedUntil = instant(row(id), "charge_locked_until");
        assertThat(lockedUntil).isBetween(
                before.plusSeconds(Duration.between(due, before).toSeconds()).minus(TOLERANCE),
                after.plusSeconds(Duration.between(due, after).toSeconds()).plus(TOLERANCE));
    }

    @Test
    @DisplayName("Declined при просрочке больше суток: ретрай не реже раза в сутки (максимальный интервал)")
    void declined_longOverdue_retriesAfterMaxDelay() {
        UUID id = insert("SUSPENDED", Instant.now().minus(Duration.ofDays(3)), false);
        when(billing.createCharge(any())).thenReturn(new ChargeOutcome.Declined("charge.account-blocked"));

        runJob();

        assertThatInWindow(instant(row(id), "charge_locked_until"), Duration.ofHours(24));
    }

    @Test
    @DisplayName("AccountClosed: подписка отменяется")
    void accountClosed_cancelsSubscription() {
        UUID id = insert("ACTIVE", Instant.now().minus(Duration.ofMinutes(1)), false);
        when(billing.createCharge(any())).thenReturn(new ChargeOutcome.AccountClosed());

        runJob();

        assertCanceled(id);
    }

    @Test
    @DisplayName("cancelAtPeriodEnd: подписка отменяется без запроса в billing")
    void cancelAtPeriodEnd_cancelsWithoutCharge() {
        UUID id = insert("ACTIVE", Instant.now().minus(Duration.ofMinutes(1)), true);

        runJob();

        verifyNoInteractions(billing);
        assertCanceled(id);
    }

    @Test
    @DisplayName("SUSPENDED дольше грейс-периода: отменяется без запроса в billing")
    void suspendedPastGracePeriod_cancelsWithoutCharge() {
        UUID id = insert("SUSPENDED", Instant.now().minus(Duration.ofDays(31)), false);

        runJob();

        verifyNoInteractions(billing);
        assertCanceled(id);
    }

    @Test
    @DisplayName("ACTIVE дольше грейс-периода (лежал сервис) не отменяется, а списывается")
    void activePastGracePeriod_isStillCharged() {
        UUID id = insert("ACTIVE", Instant.now().minus(Duration.ofDays(31)), false);
        when(billing.createCharge(any())).thenReturn(new ChargeOutcome.Charged(UUID.randomUUID()));

        runJob();

        assertThat(row(id).get("status")).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Ошибка billing по одной подписке: она не меняется и остаётся под lease, остальные обрабатываются")
    void billingError_leavesSubscriptionLockedAndProcessesOthers() {
        UUID failing = insert("ACTIVE", Instant.now().minus(Duration.ofMinutes(2)), false);
        UUID ok = insert("ACTIVE", Instant.now().minus(Duration.ofMinutes(1)), false);
        when(billing.createCharge(argThat(dto -> dto != null && dto.idempotencyKey().startsWith(failing.toString()))))
                .thenThrow(HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "", null, null, null));
        when(billing.createCharge(argThat(dto -> dto != null && dto.idempotencyKey().startsWith(ok.toString()))))
                .thenReturn(new ChargeOutcome.Charged(UUID.randomUUID()));

        assertThatCode(this::runJob).doesNotThrowAnyException();

        Map<String, Object> failed = row(failing);
        assertThat(failed.get("status")).isEqualTo("ACTIVE");
        assertThat(failed.get("last_charge_transaction_id")).isNull();
        //lease не сняли: подписку возьмут снова только после его истечения
        assertThat(instant(failed, "charge_locked_until")).isAfter(after);
        assertThat(row(ok).get("last_charge_transaction_id")).isNotNull();
    }

    @Test
    @DisplayName("Подписку отменили, пока шёл запрос в billing: Charged не возвращает её к жизни")
    void canceledDuringCharge_staysCanceled() {
        UUID id = insert("ACTIVE", Instant.now().minus(Duration.ofMinutes(1)), false);
        when(billing.createCharge(any())).thenAnswer(invocation -> {
            jdbc.update("update subscriptions set status = 'CANCELED', canceled_at = now() where id = ?", id);
            return new ChargeOutcome.Charged(UUID.randomUUID());
        });

        assertThatCode(this::runJob).doesNotThrowAnyException();

        Map<String, Object> row = row(id);
        assertThat(row.get("status")).isEqualTo("CANCELED");
        assertThat(row.get("last_charge_transaction_id")).isNull();
    }

    private void runJob() {
        before = Instant.now();
        job.chargeDueSubscriptions();
        after = Instant.now();
    }

    //Значение, которое джоба посчитала как now + offset, лежит между before + offset и after + offset
    private void assertThatInWindow(Instant actual, Duration offset) {
        assertThat(actual).isBetween(before.plus(offset).minus(TOLERANCE), after.plus(offset).plus(TOLERANCE));
    }

    private void assertCanceled(UUID id) {
        Map<String, Object> row = row(id);
        assertThat(row.get("status")).isEqualTo("CANCELED");
        assertThatInWindow(instant(row, "canceled_at"), Duration.ZERO);
        assertThat(row.get("charge_locked_until")).isNull();
    }

    private UUID insert(String status, Instant nextChargeAt, boolean cancelAtPeriodEnd) {
        return jdbc.queryForObject("""
                insert into subscriptions (id, user_id, tariff_id, status, price, current_period_start, next_charge_at, cancel_at_period_end)
                values (gen_random_uuid(), ?, ?, ?, 499.00, ?, ?, ?)
                returning id
                """, UUID.class, UUID.randomUUID(), tariffId, status,
                Timestamp.from(nextChargeAt.minus(Duration.ofDays(30))), Timestamp.from(nextChargeAt), cancelAtPeriodEnd);
    }

    private UUID userId(UUID id) {
        return jdbc.queryForObject("select user_id from subscriptions where id = ?", UUID.class, id);
    }

    private Map<String, Object> row(UUID id) {
        return jdbc.queryForMap("select * from subscriptions where id = ?", id);
    }

    private static Instant instant(Map<String, Object> row, String column) {
        return ((Timestamp) row.get(column)).toInstant();
    }
}

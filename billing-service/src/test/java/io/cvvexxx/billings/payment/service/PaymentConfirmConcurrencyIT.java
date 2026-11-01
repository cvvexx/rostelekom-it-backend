package io.cvvexxx.billings.payment.service;

import io.cvvexxx.billings.account.entity.AccountStatus;
import io.cvvexxx.billings.account.exception.AccountClosedException;
import io.cvvexxx.billings.account.exception.AccountHasPendingPaymentsException;
import io.cvvexxx.billings.account.service.AccountService;
import io.cvvexxx.billings.payment.dto.PaymentDto;
import io.cvvexxx.billings.payment.entity.PaymentStatus;
import io.cvvexxx.billings.payment.exception.PaymentStatusConflictException;
import io.cvvexxx.billings.payment.provider.stub.StubPaymentProvider;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@EnabledIfDockerAvailable
class PaymentConfirmConcurrencyIT {

    private static final int THREADS = 8;

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private AccountService accountService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private StubPaymentProvider stubPaymentProvider;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Провайдер прислал один webhook несколько раз одновременно: деньги зачислены один раз")
    void parallelDuplicateWebhooks_creditOnce() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID accountId = accountService.openAccount(userId).account().id();
        UUID paymentId = createPayment(userId, "100.00");
        String providerPaymentId = "stub-" + paymentId;
        stubPaymentProvider.markPaid(providerPaymentId);

        List<Object> results = runInParallel(i -> () -> paymentService.handleProviderNotification(providerPaymentId));

        assertThat(results).allSatisfy(r ->
                assertThat(r).isInstanceOfSatisfying(PaymentDto.class, p -> assertThat(p.status()).isEqualTo(PaymentStatus.SUCCEEDED)));
        assertThat(balance(accountId)).isEqualByComparingTo("100.00");
        assertThat(topUps(accountId)).isEqualTo(1);
    }

    @Test
    @DisplayName("Параллельное подтверждение одного платежа: все получают SUCCEEDED, деньги зачислены один раз")
    void parallelConfirmSamePayment_creditsOnce() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID accountId = accountService.openAccount(userId).account().id();
        UUID paymentId = createPayment(userId, "100.00");

        List<Object> results = runInParallel(i -> () -> paymentService.confirmPayment(paymentId));

        assertThat(results).allSatisfy(r ->
                assertThat(r).isInstanceOfSatisfying(PaymentDto.class, p -> assertThat(p.status()).isEqualTo(PaymentStatus.SUCCEEDED)));
        assertThat(balance(accountId)).isEqualByComparingTo("100.00");
        assertThat(topUps(accountId)).isEqualTo(1);
    }

    @Test
    @DisplayName("Параллельное подтверждение разных платежей одного счёта: баланс равен сумме, без lost update")
    void parallelConfirmDifferentPayments_sumsBalance() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID accountId = accountService.openAccount(userId).account().id();
        List<UUID> paymentIds = IntStream.range(0, THREADS)
                .mapToObj(i -> createPayment(userId, "10.00"))
                .toList();

        List<Object> results = runInParallel(i -> () -> paymentService.confirmPayment(paymentIds.get(i)));

        assertThat(results).allMatch(PaymentDto.class::isInstance);
        assertThat(balance(accountId)).isEqualByComparingTo("80.00");
        assertThat(topUps(accountId)).isEqualTo(THREADS);
    }

    @Test
    @DisplayName("Параллельные confirm и cancel одного платежа: побеждает один, остальные получают 409, баланс согласован")
    void parallelConfirmAndCancel_singleWinner() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID accountId = accountService.openAccount(userId).account().id();
        UUID paymentId = createPayment(userId, "100.00");

        List<Object> results = runInParallel(i -> i % 2 == 0
                ? () -> paymentService.confirmPayment(paymentId)
                : () -> paymentService.cancelPayment(userId, paymentId, false));

        PaymentStatus finalStatus = PaymentStatus.valueOf(
                jdbc.queryForObject("select status from payments where id = ?", String.class, paymentId));
        assertThat(results).allSatisfy(r -> {
            if (r instanceof PaymentDto p) {
                assertThat(p.status()).isEqualTo(finalStatus);
            } else {
                assertThat(r).isInstanceOf(PaymentStatusConflictException.class);
            }
        });
        if (finalStatus == PaymentStatus.SUCCEEDED) {
            assertThat(balance(accountId)).isEqualByComparingTo("100.00");
            assertThat(topUps(accountId)).isEqualTo(1);
        } else {
            assertThat(balance(accountId)).isEqualByComparingTo("0");
            assertThat(topUps(accountId)).isZero();
        }
    }

    @Test
    @DisplayName("Параллельные создание платежей и закрытие счёта: закрытый счёт никогда не остаётся с неоплаченным платежом")
    void parallelCreatePaymentAndClose_noPendingOnClosedAccount() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID accountId = accountService.openAccount(userId).account().id();

        List<Object> results = runInParallel(i -> i == 0
                ? () -> {
                    accountService.changeStatus(accountId, AccountStatus.CLOSED);
                    return null;
                }
                : () -> paymentService.createPayment(userId, new BigDecimal("10.00"), UUID.randomUUID().toString()));

        //Каждый поток либо отработал, либо получил ожидаемый отказ: платёж на закрытый счёт или закрытие с неоплаченными
        assertThat(results).allSatisfy(r -> {
            if (r instanceof Throwable) {
                assertThat(r).isInstanceOfAny(AccountClosedException.class, AccountHasPendingPaymentsException.class);
            }
        });
        String accountStatus = jdbc.queryForObject("select status from accounts where id = ?", String.class, accountId);
        Integer pending = jdbc.queryForObject(
                "select count(*) from payments where account_id = ? and status = 'PENDING'", Integer.class, accountId);
        if ("CLOSED".equals(accountStatus)) {
            assertThat(pending).isZero();
        } else {
            assertThat(pending).isPositive();
        }
    }

    //Запускает THREADS задач одновременно, возвращает результат или выброшенное исключение каждой
    private List<Object> runInParallel(IntFunction<Callable<PaymentDto>> task) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
            List<Future<PaymentDto>> futures = IntStream.range(0, THREADS)
                    .mapToObj(i -> {
                        Callable<PaymentDto> callable = task.apply(i);
                        return pool.submit(() -> {
                            start.await();
                            return callable.call();
                        });
                    })
                    .toList();
            start.countDown();

            return futures.stream().map(f -> {
                try {
                    return (Object) f.get();
                } catch (ExecutionException e) {
                    return e.getCause();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }).toList();
        }
    }

    private UUID createPayment(UUID userId, String amount) {
        return paymentService.createPayment(userId, new BigDecimal(amount), UUID.randomUUID().toString()).id();
    }

    private BigDecimal balance(UUID accountId) {
        return jdbc.queryForObject("select balance from accounts where id = ?", BigDecimal.class, accountId);
    }

    private Integer topUps(UUID accountId) {
        return jdbc.queryForObject(
                "select count(*) from transactions where account_id = ? and type = 'TOP_UP'", Integer.class, accountId);
    }
}

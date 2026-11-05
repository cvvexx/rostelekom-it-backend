package io.cvvexxx.billings.charges.service;

import io.cvvexxx.billings.charges.dto.ChargeResult;
import io.cvvexxx.billings.charges.dto.CreateChargeDto;
import io.cvvexxx.billings.charges.exception.InsufficientFundsException;
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
class ChargeConcurrencyIT {

    private static final int THREADS = 8;
    private static final BigDecimal AMOUNT = new BigDecimal("100.00");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private ChargeService chargeService;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Параллельные повторы с одним ключом: деньги списаны один раз, все получили одно и то же списание")
    void parallelSameKey_chargesOnce() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID accountId = insertAccount(userId, "1000.00");

        List<Object> results = runInParallel(i -> () ->
                chargeService.charge(new CreateChargeDto(userId, AMOUNT, "sub:1:100", null)));

        assertThat(results).allMatch(ChargeResult.class::isInstance);
        assertThat(results).filteredOn(r -> ((ChargeResult) r).created()).hasSize(1);
        assertThat(results).extracting(r -> ((ChargeResult) r).charge().transactionId()).containsOnly(
                ((ChargeResult) results.getFirst()).charge().transactionId());
        assertThat(balance(accountId)).isEqualByComparingTo("900.00");
        assertThat(charges(accountId)).isEqualTo(1);
    }

    @Test
    @DisplayName("Параллельные списания разными ключами: баланс не уходит в минус, лишние получают нехватку денег")
    void parallelDifferentKeys_neverOverdraws() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID accountId = insertAccount(userId, "300.00");

        List<Object> results = runInParallel(i -> () ->
                chargeService.charge(new CreateChargeDto(userId, AMOUNT, "sub:" + i + ":100", null)));

        assertThat(results).filteredOn(ChargeResult.class::isInstance).hasSize(3);
        assertThat(results).filteredOn(InsufficientFundsException.class::isInstance).hasSize(THREADS - 3);
        assertThat(balance(accountId)).isEqualByComparingTo("0.00");
        assertThat(charges(accountId)).isEqualTo(3);
    }

    private UUID insertAccount(UUID userId, String balance) {
        return jdbc.queryForObject("""
                insert into accounts (id, user_id, number, balance)
                values (gen_random_uuid(), ?, nextval('account_number_seq')::text, ?)
                returning id
                """, UUID.class, userId, new BigDecimal(balance));
    }

    private BigDecimal balance(UUID accountId) {
        return jdbc.queryForObject("select balance from accounts where id = ?", BigDecimal.class, accountId);
    }

    private int charges(UUID accountId) {
        return jdbc.queryForObject("select count(*) from transactions where account_id = ? and type = 'CHARGE'",
                Integer.class, accountId);
    }

    private List<Object> runInParallel(IntFunction<Callable<ChargeResult>> task) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
            List<Future<ChargeResult>> futures = IntStream.range(0, THREADS)
                    .mapToObj(i -> {
                        Callable<ChargeResult> callable = task.apply(i);
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
}

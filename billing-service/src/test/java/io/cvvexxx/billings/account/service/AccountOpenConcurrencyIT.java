package io.cvvexxx.billings.account.service;

import io.cvvexxx.billings.account.dto.OpenAccountResult;
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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@EnabledIfDockerAvailable
class AccountOpenConcurrencyIT {

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
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Параллельное открытие счёта одним юзером: создаётся ровно один счёт, все получают его")
    void parallelOpenAccount_createsSingleAccount() throws Exception {
        UUID userId = UUID.randomUUID();
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);

        List<OpenAccountResult> results;
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<OpenAccountResult>> futures = IntStream.range(0, threads)
                    .mapToObj(i -> pool.submit(() -> {
                        start.await();
                        return accountService.openAccount(userId);
                    }))
                    .toList();
            start.countDown();

            results = futures.stream().map(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).toList();
        }

        assertThat(results).filteredOn(OpenAccountResult::created).hasSize(1);
        assertThat(results).extracting(r -> r.account().id()).containsOnly(results.getFirst().account().id());
        assertThat(jdbc.queryForObject("select count(*) from accounts where user_id = ?", Integer.class, userId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Разные пользователи получают разные, возрастающие номера счетов")
    void differentUsersGetIncreasingNumbers() {
        long first = Long.parseLong(accountService.openAccount(UUID.randomUUID()).account().number());
        long second = Long.parseLong(accountService.openAccount(UUID.randomUUID()).account().number());

        assertThat(first).isGreaterThanOrEqualTo(1_000_000_001L);
        assertThat(second).isGreaterThan(first);
    }
}

package io.cvvexxx.users.token.service;

import io.cvvexxx.users.token.exception.InvalidRefreshTokenException;
import io.cvvexxx.users.token.repository.RefreshTokenRepository;
import io.cvvexxx.users.user.entity.User;
import io.cvvexxx.users.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.datasource.driver-class-name=org.postgresql.Driver"
})
@ActiveProfiles("test")
@Testcontainers
@EnabledIfDockerAvailable
class RefreshTokenConcurrencyIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private TokenService tokenService;
    @Autowired
    private UserService userService;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    @DisplayName("Параллельный refresh одним токеном: новую пару получает ровно один поток")
    void parallelRefreshWithSameToken_onlyOneSucceeds() throws Exception {
        User user = userService.findOrCreateByPhone("+79990000001", "Иванов", "Иван", null);
        String refreshToken = tokenService.issueTokens(user).refreshToken();

        int threads = 8;
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<? extends Future<?>> futures = IntStream.range(0, threads)
                    .mapToObj(i -> pool.submit(() -> {
                        start.await();
                        try {
                            tokenService.refresh(refreshToken);
                            succeeded.incrementAndGet();
                        } catch (InvalidRefreshTokenException e) {
                            rejected.incrementAndGet();
                        }
                        return null;
                    }))
                    .toList();

            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        }

        assertThat(succeeded.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(threads - 1);
    }

    @Test
    @DisplayName("Повторное использование отозванного токена гасит все сессии пользователя")
    void reusingRevokedToken_revokesEverySession() {
        User user = userService.findOrCreateByPhone("+79990000002", "Иванов", "Иван", null);
        String first = tokenService.issueTokens(user).refreshToken();
        String second = tokenService.refresh(first).refreshToken();

        try {
            tokenService.refresh(first);
        } catch (InvalidRefreshTokenException expected) {
            // старый токен должен быть отвергнут
        }

        assertThat(refreshTokenRepository.findAll())
                .filteredOn(token -> token.getUser().getId().equals(user.getId()))
                .allMatch(token -> token.getRevokedAt() != null);

        assertThat(second).isNotBlank();
    }
}

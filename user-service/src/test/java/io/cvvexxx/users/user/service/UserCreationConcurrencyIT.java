package io.cvvexxx.users.user.service;

import io.cvvexxx.users.user.entity.Role;
import io.cvvexxx.users.user.entity.User;
import io.cvvexxx.users.user.repository.UserRepository;
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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
class UserCreationConcurrencyIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Параллельный вход с одного нового номера: создаётся ровно один юзер, все потоки получают его")
    void parallelFindOrCreate_createsSingleUser() throws Exception {
        String phone = "+79990000101";
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);

        List<UUID> ids;
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<? extends Future<UUID>> futures = IntStream.range(0, threads)
                    .mapToObj(i -> pool.submit(() -> {
                        start.await();
                        return userService.findOrCreateByPhone(phone, "Иванов", "Иван", null).getId();
                    }))
                    .toList();

            start.countDown();
            ids = futures.stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception e) {
                    throw new AssertionError("findOrCreateByPhone failed under concurrency", e);
                }
            }).toList();
        }

        assertThat(ids).hasSize(threads).containsOnly(ids.getFirst());

        User user = userRepository.findByPhone(phone).orElseThrow();
        assertThat(user.getId()).isEqualTo(ids.getFirst());
        assertThat(user.getRoles()).containsExactly(Role.USER);
        assertThat(user.getCreatedAt()).isNotNull();
    }
}

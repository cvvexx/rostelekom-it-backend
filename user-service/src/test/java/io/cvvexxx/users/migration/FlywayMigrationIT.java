package io.cvvexxx.users.migration;

import io.cvvexxx.users.user.entity.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.datasource.driver-class-name=org.postgresql.Driver"
})
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

    private static final AtomicInteger PHONE_SEQ = new AtomicInteger();

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Вся цепочка миграций применяется к чистому Postgres")
    void allMigrationsApply() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success = true", Integer.class);

        assertThat(applied).isEqualTo(5);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("Каждая роль из enum проходит check-constraint")
    void everyRoleFromEnumPassesCheckConstraint(Role role) {
        UUID userId = insertUser();

        assertThatCode(() -> jdbc.update(
                "insert into user_roles (user_id, role) values (?, ?)", userId, role.name()))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "user", "ROLE_USER", ""})
    @DisplayName("Роль, которой нет в enum, отклоняется на вставке")
    void unknownRoleIsRejectedOnInsert(String badRole) {
        UUID userId = insertUser();

        assertThatThrownBy(() -> jdbc.update(
                "insert into user_roles (user_id, role) values (?, ?)", userId, badRole))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_user_roles_role");
    }

    @Test
    @DisplayName("Одна и та же роль не может задвоиться")
    void sameRoleCannotBeAddedTwice() {
        UUID userId = insertUser();
        jdbc.update("insert into user_roles (user_id, role) values (?, 'USER')", userId);

        assertThatThrownBy(() -> jdbc.update(
                "insert into user_roles (user_id, role) values (?, 'USER')", userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Удаление пользователя забирает с собой его роли")
    void deletingUserCascadesToRoles() {
        UUID userId = insertUser();
        jdbc.update("insert into user_roles (user_id, role) values (?, 'USER')", userId);

        jdbc.update("delete from users where id = ?", userId);

        Integer left = jdbc.queryForObject(
                "select count(*) from user_roles where user_id = ?", Integer.class, userId);
        assertThat(left).isZero();
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into users (id, phone) values (?, ?)",
                id, "+7999%07d".formatted(PHONE_SEQ.incrementAndGet()));
        return id;
    }
}

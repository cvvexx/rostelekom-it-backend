package io.cvvexxx.billings.charges.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@EnabledIfDockerAvailable
class ChargeControllerIT {

    private static final String CHARGES = "/internal/billing/charges";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Списание: 201, баланс уменьшился, CHARGE записан с ключом")
    void charge_debitsBalance() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID accountId = insertAccount(userId, "500.00", "ACTIVE");

        charge(userId, "199.90", "sub:1:100")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionId").isNotEmpty())
                .andExpect(jsonPath("$.balanceAfter").value(300.10))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        assertThat(balance(accountId)).isEqualByComparingTo("300.10");
        assertThat(jdbc.queryForObject(
                "select idempotency_key from transactions where account_id = ? and type = 'CHARGE'",
                String.class, accountId)).isEqualTo("sub:1:100");
    }

    @Test
    @DisplayName("Списание всей суммы баланса разрешено, баланс становится 0")
    void charge_exactBalance_isAllowed() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID accountId = insertAccount(userId, "100.00", "ACTIVE");

        charge(userId, "100.00", "sub:1:100").andExpect(status().isCreated());

        assertThat(balance(accountId)).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Повтор с тем же ключом: 200, то же списание, деньги списаны один раз")
    void charge_sameKey_isIdempotent() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID accountId = insertAccount(userId, "500.00", "ACTIVE");

        String first = charge(userId, "100.00", "sub:1:100")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        charge(userId, "100.00", "sub:1:100")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(JsonPath.<String>read(first, "$.transactionId")))
                .andExpect(jsonPath("$.balanceAfter").value(400.00));

        assertThat(balance(accountId)).isEqualByComparingTo("400.00");
        assertThat(charges(accountId)).isEqualTo(1);
    }

    @Test
    @DisplayName("Счёт заблокировали после списания: повтор всё равно получает своё списание (200), а не 409")
    void charge_retryAfterAccountBlocked_returnsExistingCharge() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID accountId = insertAccount(userId, "500.00", "ACTIVE");
        charge(userId, "100.00", "sub:1:100").andExpect(status().isCreated());
        jdbc.update("update accounts set status = 'BLOCKED' where id = ?", accountId);

        charge(userId, "100.00", "sub:1:100").andExpect(status().isOk());
    }

    @Test
    @DisplayName("Не хватает денег: 409 charge.insufficient-funds, баланс не тронут, CHARGE не записан")
    void charge_insufficientFunds_returns409() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID accountId = insertAccount(userId, "99.99", "ACTIVE");

        charge(userId, "100.00", "sub:1:100")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("charge.insufficient-funds"));

        assertThat(balance(accountId)).isEqualByComparingTo("99.99");
        assertThat(charges(accountId)).isZero();
    }

    @ParameterizedTest
    @CsvSource({"BLOCKED, charge.account-blocked", "CLOSED, charge.account-closed"})
    @DisplayName("Неактивный счёт: 409 со своим code, деньги не списаны")
    void charge_inactiveAccount_returns409(String accountStatus, String code) throws Exception {
        UUID userId = UUID.randomUUID();
        UUID accountId = insertAccount(userId, "500.00", accountStatus);

        charge(userId, "100.00", "sub:1:100")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(code));

        assertThat(balance(accountId)).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("Нет счёта: 404 account.not-found-by-user")
    void charge_withoutAccount_returns404() throws Exception {
        charge(UUID.randomUUID(), "100.00", "sub:1:100")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("account.not-found-by-user"));
    }

    @Test
    @DisplayName("Невалидное тело: 400 с сообщением по каждому полю")
    void charge_invalidBody_returns400() throws Exception {
        String body = """
                {"userId": null, "amount": -1, "idempotencyKey": "", "description": "%s"}
                """.formatted("x".repeat(256));

        mockMvc.perform(post(CHARGES).with(service()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.userId").value("Id пользователя обязателен"))
                .andExpect(jsonPath("$.errors.amount").value("Сумма списания должна быть больше нуля"))
                .andExpect(jsonPath("$.errors.idempotencyKey").value("Ключ идемпотентности обязателен"))
                .andExpect(jsonPath("$.errors.description").value("Описание списания должно быть не длиннее 255 символов"));
    }

    @Test
    @DisplayName("Пользовательский токен: 403, без токена: 401")
    void charge_withoutServiceRole_isRejected() throws Exception {
        UUID userId = UUID.randomUUID();
        insertAccount(userId, "500.00", "ACTIVE");
        String body = chargeBody(userId, "100.00", "sub:1:100");

        mockMvc.perform(post(CHARGES).with(jwt().jwt(jwt -> jwt.subject(userId.toString())))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(CHARGES).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    private RequestPostProcessor service() {
        return jwt().jwt(jwt -> jwt.subject("service:services-service"))
                .authorities(new SimpleGrantedAuthority("ROLE_SERVICE"));
    }

    private ResultActions charge(UUID userId, String amount, String idempotencyKey) throws Exception {
        return mockMvc.perform(post(CHARGES).with(service())
                .contentType(MediaType.APPLICATION_JSON)
                .content(chargeBody(userId, amount, idempotencyKey)));
    }

    private String chargeBody(UUID userId, String amount, String idempotencyKey) {
        return """
                {"userId": "%s", "amount": %s, "idempotencyKey": "%s", "description": "Абонплата"}
                """.formatted(userId, amount, idempotencyKey);
    }

    private UUID insertAccount(UUID userId, String balance, String accountStatus) {
        return jdbc.queryForObject("""
                insert into accounts (id, user_id, number, balance, status)
                values (gen_random_uuid(), ?, nextval('account_number_seq')::text, ?, ?)
                returning id
                """, UUID.class, userId, new BigDecimal(balance), accountStatus);
    }

    private BigDecimal balance(UUID accountId) {
        return jdbc.queryForObject("select balance from accounts where id = ?", BigDecimal.class, accountId);
    }

    private int charges(UUID accountId) {
        return jdbc.queryForObject("select count(*) from transactions where account_id = ? and type = 'CHARGE'",
                Integer.class, accountId);
    }
}

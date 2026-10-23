package io.cvvexxx.billings.payment.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@EnabledIfDockerAvailable
class PaymentControllerIT {

    private static final String ACCOUNTS = "/api/billing/accounts";
    private static final String PAYMENTS = "/api/billing/payments";

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
    @DisplayName("POST: создаёт платёж в статусе PENDING, баланс не меняется")
    void createPayment_createsPendingPayment() throws Exception {
        UUID userId = UUID.randomUUID();
        openAccount(userId);

        createPayment(userId, "key-1", "500.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.amount").value(500.00))
                .andExpect(jsonPath("$.currency").value("RUB"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(get(ACCOUNTS + "/me").with(user(userId)))
                .andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    @DisplayName("POST: повтор с тем же ключом и суммой возвращает тот же платёж, 10.0 и 10.00 считаются одной суммой")
    void createPayment_isIdempotent() throws Exception {
        UUID userId = UUID.randomUUID();
        openAccount(userId);

        String first = createPayment(userId, "key-1", "10.00")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        createPayment(userId, "key-1", "10.0")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(JsonPath.<String>read(first, "$.id")));

        Integer count = jdbc.queryForObject(
                "select count(*) from payments where idempotency_key = 'key-1' and account_id = ?",
                Integer.class, UUID.fromString(JsonPath.read(accountBody(userId), "$.id")));
        assertEquals(1, count);
    }

    @Test
    @DisplayName("POST: тот же ключ с другой суммой — 422")
    void createPayment_sameKeyDifferentAmount_returns422() throws Exception {
        UUID userId = UUID.randomUUID();
        openAccount(userId);
        createPayment(userId, "key-1", "10.00").andExpect(status().isOk());

        createPayment(userId, "key-1", "20.00")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Ключ идемпотентности key-1 уже использован с суммой 10.00"))
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    @DisplayName("POST: одинаковый ключ у разных пользователей — два разных платежа")
    void createPayment_keyIsScopedToAccount() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        openAccount(alice);
        openAccount(bob);

        String aliceBody = createPayment(alice, "same-key", "10.00").andReturn().getResponse().getContentAsString();
        String bobBody = createPayment(bob, "same-key", "99.00")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertNotEquals(
                JsonPath.<String>read(aliceBody, "$.id"), JsonPath.<String>read(bobBody, "$.id"));
    }

    @Test
    @DisplayName("POST: невалидная сумма — 400 с ошибкой по полю amount")
    void createPayment_invalidAmount_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        openAccount(userId);

        Map<String, String> expectedErrors = Map.of(
                "0", "Сумма платежа должна быть больше нуля",
                "-5.00", "Сумма платежа должна быть больше нуля",
                "10.555", "Сумма платежа должна содержать не больше 17 цифр в целой части и 2 после запятой",
                "null", "Сумма платежа обязательна");

        for (Map.Entry<String, String> expected : expectedErrors.entrySet()) {
            createPayment(userId, UUID.randomUUID().toString(), expected.getKey())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Ошибка валидации"))
                    .andExpect(jsonPath("$.errors.amount").value(expected.getValue()));
        }
    }

    @Test
    @DisplayName("POST: без заголовка Idempotency-Key — 400")
    void createPayment_withoutIdempotencyKey_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        openAccount(userId);

        mockMvc.perform(post(PAYMENTS).with(user(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 10.00}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST: Idempotency-Key длиннее 64 символов — 400, а не 500 от БД")
    void createPayment_tooLongIdempotencyKey_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        openAccount(userId);

        createPayment(userId, "k".repeat(65), "10.00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.idempotencyKey").value("Idempotency-Key должен быть не длиннее 64 символов"));
    }

    @Test
    @DisplayName("POST: без счёта — 404")
    void createPayment_withoutAccount_returns404() throws Exception {
        createPayment(UUID.randomUUID(), "key-1", "10.00")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(matchesPattern("Счёт пользователя [0-9a-f-]{36} не найден")))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("POST: закрытый счёт — 409, заблокированный пополнять можно")
    void createPayment_closedAccount_returns409() throws Exception {
        UUID closed = UUID.randomUUID();
        UUID blocked = UUID.randomUUID();
        UUID closedAccountId = openAccount(closed);
        setStatus(closedAccountId, "CLOSED");
        setStatus(openAccount(blocked), "BLOCKED");

        createPayment(closed, "key-1", "10.00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Счёт с id " + closedAccountId + " закрыт"))
                .andExpect(jsonPath("$.status").value(409));
        createPayment(blocked, "key-1", "10.00")
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /{id}: свой платёж — 200, чужой и несуществующий — 404")
    void getMyPayment_isScopedToOwnAccount() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        openAccount(alice);
        openAccount(bob);
        String paymentId = JsonPath.read(
                createPayment(alice, "key-1", "10.00").andReturn().getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get(PAYMENTS + "/" + paymentId).with(user(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId));
        mockMvc.perform(get(PAYMENTS + "/" + paymentId).with(user(bob)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Платёж с id " + paymentId + " не найден"));
        mockMvc.perform(get(PAYMENTS + "/" + UUID.randomUUID()).with(user(alice)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET: только свои платежи, новые сверху, с пагинацией")
    void getMyPayments_returnsOwnNewestFirst() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID accountId = openAccount(userId);
        UUID otherAccountId = openAccount(UUID.randomUUID());
        OffsetDateTime now = OffsetDateTime.now();
        insertPayment(accountId, "100.00", now.minusHours(2));
        insertPayment(accountId, "200.00", now.minusHours(1));
        insertPayment(accountId, "300.00", now);
        insertPayment(otherAccountId, "999.00", now);

        mockMvc.perform(get(PAYMENTS).param("size", "2").with(user(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].amount").value(300.00))
                .andExpect(jsonPath("$.content[1].amount").value(200.00))
                .andExpect(jsonPath("$.page.totalElements").value(3));
    }

    private RequestPostProcessor user(UUID userId) {
        return jwt().jwt(jwt -> jwt.subject(userId.toString()));
    }

    private ResultActions createPayment(UUID userId, String idempotencyKey, String amount) throws Exception {
        return mockMvc.perform(post(PAYMENTS).with(user(userId))
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": %s}".formatted(amount)));
    }

    private UUID openAccount(UUID userId) throws Exception {
        String body = mockMvc.perform(post(ACCOUNTS).with(user(userId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private String accountBody(UUID userId) throws Exception {
        return mockMvc.perform(get(ACCOUNTS + "/me").with(user(userId)))
                .andReturn().getResponse().getContentAsString();
    }

    private void setStatus(UUID accountId, String status) {
        jdbc.update("update accounts set status = ? where id = ?", status, accountId);
    }

    private void insertPayment(UUID accountId, String amount, OffsetDateTime createdAt) {
        jdbc.update("""
                insert into payments (id, account_id, amount, provider, idempotency_key, created_at)
                values (?, ?, ?::numeric, 'TEST', ?, ?)
                """, UUID.randomUUID(), accountId, amount, UUID.randomUUID().toString(), createdAt);
    }
}

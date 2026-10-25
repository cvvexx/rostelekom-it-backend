package io.cvvexxx.billings.payment.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import io.cvvexxx.billings.payment.service.PaymentService;
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
    @Autowired
    private PaymentService paymentService;

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

    @Test
    @DisplayName("GET: sort только по разрешённым полям, остальное -> 400")
    void getMyPayments_sortWhitelist() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID accountId = openAccount(userId);
        OffsetDateTime now = OffsetDateTime.now();
        insertPayment(accountId, "300.00", now.minusHours(1));
        insertPayment(accountId, "100.00", now);

        mockMvc.perform(get(PAYMENTS).param("sort", "amount,asc").with(user(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].amount").value(100.00))
                .andExpect(jsonPath("$.content[1].amount").value(300.00));

        mockMvc.perform(get(PAYMENTS).param("sort", "[\"string\"]").with(user(userId)))
                .andExpect(status().isBadRequest());
        //Поле есть в сущности, но не в списке разрешённых
        mockMvc.perform(get(PAYMENTS).param("sort", "idempotencyKey").with(user(userId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Оплата через заглушку провайдера: SUCCEEDED, баланс пополнен, повторный webhook не зачисляет второй раз")
    void stubPay_creditsBalanceOnce() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID accountId = openAccount(userId);
        String paymentId = createPaymentId(userId, "500.00");
        String confirmationUrl = confirmationUrl(userId, paymentId);
        assertEquals("/api/billing/stub/pay/stub-" + paymentId, confirmationUrl);

        mockMvc.perform(post(confirmationUrl).with(user(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        mockMvc.perform(post(confirmationUrl).with(user(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        mockMvc.perform(get(ACCOUNTS + "/me").with(user(userId)))
                .andExpect(jsonPath("$.balance").value(500.00));
        Integer count = jdbc.queryForObject(
                "select count(*) from transactions where account_id = ? and type = 'TOP_UP'", Integer.class, accountId);
        assertEquals(1, count);
    }

    @Test
    @DisplayName("POST /{id}/confirm: юзер подтвердить даже свой платёж не может — 403, баланс не меняется")
    void confirmPayment_byUser_returns403() throws Exception {
        UUID userId = UUID.randomUUID();
        openAccount(userId);
        String paymentId = createPaymentId(userId, "10.00");

        confirm(user(userId), paymentId)
                .andExpect(status().isForbidden());

        mockMvc.perform(get(PAYMENTS + "/" + paymentId).with(user(userId)))
                .andExpect(jsonPath("$.status").value("PENDING"));
        mockMvc.perform(get(ACCOUNTS + "/me").with(user(userId)))
                .andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    @DisplayName("POST /{id}/confirm: админ без счёта подтверждает, деньги уходят владельцу платежа")
    void confirmPayment_byAdmin_creditsOwner() throws Exception {
        UUID owner = UUID.randomUUID();
        openAccount(owner);
        String paymentId = createPaymentId(owner, "300.00");

        confirm(admin(), paymentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        mockMvc.perform(get(ACCOUNTS + "/me").with(user(owner)))
                .andExpect(jsonPath("$.balance").value(300.00));
    }

    @Test
    @DisplayName("POST /{id}/cancel: чужой платёж — 404, статус не меняется")
    void cancelPayment_foreignPayment_returns404() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        openAccount(alice);
        openAccount(bob);
        String paymentId = createPaymentId(alice, "10.00");

        cancel(user(bob), paymentId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Платёж с id " + paymentId + " не найден"));

        mockMvc.perform(get(PAYMENTS + "/" + paymentId).with(user(alice)))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("POST /{id}/cancel: CANCELED, повтор — 200, подтвердить отменённый (админом или webhook'ом) — 409")
    void cancelPayment_thenConfirm_returns409() throws Exception {
        UUID userId = UUID.randomUUID();
        openAccount(userId);
        String paymentId = createPaymentId(userId, "10.00");

        cancel(user(userId), paymentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));
        cancel(user(userId), paymentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));
        confirm(admin(), paymentId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Платёж " + paymentId + " находится в статусе CANCELED"));
        mockMvc.perform(post(confirmationUrl(userId, paymentId)).with(user(userId)))
                .andExpect(status().isConflict());

        mockMvc.perform(get(ACCOUNTS + "/me").with(user(userId)))
                .andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    @DisplayName("POST /{id}/cancel: оплаченный платёж отменить нельзя — 409")
    void cancelPayment_succeeded_returns409() throws Exception {
        UUID userId = UUID.randomUUID();
        openAccount(userId);
        String paymentId = createPaymentId(userId, "10.00");
        mockMvc.perform(post(confirmationUrl(userId, paymentId)).with(user(userId))).andExpect(status().isOk());

        cancel(user(userId), paymentId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Платёж " + paymentId + " находится в статусе SUCCEEDED"));
    }

    @Test
    @DisplayName("POST /{id}/confirm: счёт закрыли после создания платежа — 409, баланс не меняется")
    void confirmPayment_closedAccount_returns409() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID accountId = openAccount(userId);
        String paymentId = createPaymentId(userId, "10.00");
        setStatus(accountId, "CLOSED");

        confirm(admin(), paymentId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Счёт с id " + accountId + " закрыт"));

        assertEquals(0, jdbc.queryForObject(
                "select balance from accounts where id = ?", BigDecimal.class, accountId).signum());
    }

    @Test
    @DisplayName("POST: платёж создаётся у провайдера, повтор с тем же ключом провайдера второй раз не дёргает")
    void createPayment_savesProviderPayment() throws Exception {
        UUID userId = UUID.randomUUID();
        openAccount(userId);

        String first = createPayment(userId, "key-1", "10.00")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String paymentId = JsonPath.read(first, "$.id");
        createPayment(userId, "key-1", "10.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmationUrl").value(JsonPath.<String>read(first, "$.confirmationUrl")));

        Map<String, Object> row = jdbc.queryForMap(
                "select provider, provider_payment_id from payments where id = ?", UUID.fromString(paymentId));
        assertEquals("STUB", row.get("provider"));
        assertEquals("stub-" + paymentId, row.get("provider_payment_id"));
    }

    @Test
    @DisplayName("Уведомление о платеже, который у провайдера не оплачен (подделка или сверка): остаётся PENDING, баланс не меняется")
    void providerNotification_providerSaysNotPaid_keepsPending() throws Exception {
        UUID userId = UUID.randomUUID();
        openAccount(userId);
        String paymentId = createPaymentId(userId, "10.00");

        assertEquals("PENDING", paymentService.handleProviderNotification("stub-" + paymentId).status().name());

        mockMvc.perform(get(PAYMENTS + "/" + paymentId).with(user(userId)))
                .andExpect(jsonPath("$.status").value("PENDING"));
        mockMvc.perform(get(ACCOUNTS + "/me").with(user(userId)))
                .andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    @DisplayName("Оплата через заглушку по неизвестному id провайдера — 404")
    void stubPay_unknownProviderPaymentId_returns404() throws Exception {
        UUID userId = UUID.randomUUID();
        openAccount(userId);

        mockMvc.perform(post("/api/billing/stub/pay/stub-unknown").with(user(userId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Платёж провайдера STUB с id stub-unknown не найден"));
    }

    @Test
    @DisplayName("Оплата через заглушку без JWT — 401")
    void stubPay_withoutJwt_returns401() throws Exception {
        mockMvc.perform(post("/api/billing/stub/pay/stub-" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    private RequestPostProcessor user(UUID userId) {
        return jwt().jwt(jwt -> jwt.subject(userId.toString()));
    }

    private RequestPostProcessor admin() {
        return jwt().jwt(jwt -> jwt.subject(UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private String createPaymentId(UUID userId, String amount) throws Exception {
        String body = createPayment(userId, UUID.randomUUID().toString(), amount)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String confirmationUrl(UUID userId, String paymentId) throws Exception {
        String body = mockMvc.perform(get(PAYMENTS + "/" + paymentId).with(user(userId)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.confirmationUrl");
    }

    private ResultActions confirm(RequestPostProcessor auth, String paymentId) throws Exception {
        return mockMvc.perform(post(PAYMENTS + "/" + paymentId + "/confirm").with(auth));
    }

    private ResultActions cancel(RequestPostProcessor auth, String paymentId) throws Exception {
        return mockMvc.perform(post(PAYMENTS + "/" + paymentId + "/cancel").with(auth));
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

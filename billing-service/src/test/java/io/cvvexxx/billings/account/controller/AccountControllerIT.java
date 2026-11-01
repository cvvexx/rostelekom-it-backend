package io.cvvexxx.billings.account.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
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
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@EnabledIfDockerAvailable
class AccountControllerIT {

    private static final String ACCOUNTS = "/api/billing/accounts";

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
    @DisplayName("POST: первый вызов открывает счёт (201), повторный идемпотентен (200, тот же счёт)")
    void openAccount_isIdempotent() throws Exception {
        UUID userId = UUID.randomUUID();
        String first = mockMvc.perform(post(ACCOUNTS).with(user(userId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number", matchesPattern("\\d{10}")))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.currency").value("RUB"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post(ACCOUNTS).with(user(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(JsonPath.<String>read(first, "$.id")))
                .andExpect(jsonPath("$.number").value(JsonPath.<String>read(first, "$.number")));
    }

    @Test
    @DisplayName("GET /me: каждый пользователь видит только свой счёт")
    void getMyAccount_returnsOwnAccount() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        String aliceAccountId = openAccount(alice);
        String bobAccountId = openAccount(bob);

        mockMvc.perform(get(ACCOUNTS + "/me").with(user(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(aliceAccountId));
        mockMvc.perform(get(ACCOUNTS + "/me").with(user(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bobAccountId));
    }

    @Test
    @DisplayName("GET /me/transactions: новые сверху, paymentId подставляется у пополнения")
    void getMyTransactions_returnsNewestFirst() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.fromString(openAccount(userId));
        UUID paymentId = insertPayment(accountId, "SUCCEEDED");
        OffsetDateTime now = OffsetDateTime.now();
        insertTransaction(accountId, "TOP_UP", "500.00", "500.00", paymentId, now.minusHours(2));
        insertTransaction(accountId, "CHARGE", "100.00", "400.00", null, now.minusHours(1));
        insertTransaction(accountId, "REFUND", "50.00", "450.00", null, now);

        mockMvc.perform(get(ACCOUNTS + "/me/transactions").with(user(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].type").value("REFUND"))
                .andExpect(jsonPath("$.content[1].type").value("CHARGE"))
                .andExpect(jsonPath("$.content[1].paymentId").doesNotExist())
                .andExpect(jsonPath("$.content[2].type").value("TOP_UP"))
                .andExpect(jsonPath("$.content[2].amount").value(500.00))
                .andExpect(jsonPath("$.content[2].balanceAfter").value(500.00))
                .andExpect(jsonPath("$.content[2].paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.size").value(20));
    }

    @Test
    @DisplayName("GET /me/transactions: пагинация и чужие транзакции не попадают в выдачу")
    void getMyTransactions_isPagedAndScopedToOwnAccount() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.fromString(openAccount(userId));
        UUID otherAccountId = UUID.fromString(openAccount(UUID.randomUUID()));
        OffsetDateTime now = OffsetDateTime.now();
        for (int i = 0; i < 5; i++) {
            insertTransaction(accountId, "CHARGE", "10.00", "0.00", null, now.minusMinutes(i));
        }
        insertTransaction(otherAccountId, "CHARGE", "10.00", "0.00", null, now);

        mockMvc.perform(get(ACCOUNTS + "/me/transactions").param("page", "1").param("size", "2")
                        .with(user(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.size").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(5))
                .andExpect(jsonPath("$.page.totalPages").value(3));
    }

    @Test
    @DisplayName("Без токена — 401 с заголовком WWW-Authenticate: Bearer")
    void withoutToken_returns401() throws Exception {
        mockMvc.perform(get(ACCOUNTS + "/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Access-токен отсутствует, истёк или недействителен"))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Bearer")));
    }

    @Test
    @DisplayName("GET /me: счёта нет — 404, счёт не создаётся")
    void getMyAccount_withoutAccount_returns404() throws Exception {
        UUID userId = UUID.randomUUID();
        mockMvc.perform(get(ACCOUNTS + "/me").with(user(userId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Счёт пользователя " + userId + " не найден"));

        mockMvc.perform(post(ACCOUNTS).with(user(userId)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("GET /{id}: админ получает счёт по id счёта, несуществующий — 404")
    void getAccountById_returnsAccountForAdmin() throws Exception {
        String accountId = openAccount(UUID.randomUUID());

        mockMvc.perform(get(ACCOUNTS + "/" + accountId).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(accountId));
        mockMvc.perform(get(ACCOUNTS + "/" + UUID.randomUUID()).with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(matchesPattern("Счёт с id [0-9a-f-]{36} не найден")));
    }

    @Test
    @DisplayName("PATCH /{id}/status: ACTIVE → BLOCKED → ACTIVE")
    void changeStatus_changesStatus() throws Exception {
        String accountId = openAccount(UUID.randomUUID());

        changeStatus(accountId, "BLOCKED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));
        changeStatus(accountId, "ACTIVE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("PATCH /{id}/status: тот же статус и любой переход из CLOSED — 409")
    void changeStatus_conflicts_return409() throws Exception {
        String accountId = openAccount(UUID.randomUUID());

        changeStatus(accountId, "ACTIVE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Статус ACTIVE уже установлен для счёта с id " + accountId));
        changeStatus(accountId, "CLOSED")
                .andExpect(status().isOk());
        changeStatus(accountId, "ACTIVE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Счёт с id " + accountId + " уже закрыт"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("PATCH /{id}/status: закрыть счёт с неоплаченным платежом — 409, счёт остаётся открытым")
    void changeStatus_closeWithPendingPayment_returns409() throws Exception {
        String accountId = openAccount(UUID.randomUUID());
        insertPayment(UUID.fromString(accountId), "PENDING");

        changeStatus(accountId, "CLOSED")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Счёт " + accountId + " нельзя закрыть, есть неоплаченные платежи"));
        mockMvc.perform(get(ACCOUNTS + "/" + accountId).with(admin()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("PATCH /{id}/status: неоплаченный платёж не мешает блокировке, а оплаченные и отменённые — закрытию")
    void changeStatus_pendingCheckOnlyForClose() throws Exception {
        String blockedId = openAccount(UUID.randomUUID());
        insertPayment(UUID.fromString(blockedId), "PENDING");
        changeStatus(blockedId, "BLOCKED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));

        String closedId = openAccount(UUID.randomUUID());
        insertPayment(UUID.fromString(closedId), "SUCCEEDED");
        insertPayment(UUID.fromString(closedId), "CANCELED");
        changeStatus(closedId, "CLOSED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    @DisplayName("PATCH /{id}/status: без статуса — 400, несуществующий счёт — 404")
    void changeStatus_invalidRequest() throws Exception {
        String accountId = openAccount(UUID.randomUUID());

        mockMvc.perform(patch(ACCOUNTS + "/" + accountId + "/status").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.accountStatus").value("Статус счёта обязателен"));
        changeStatus(UUID.randomUUID().toString(), "BLOCKED")
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /{id} и PATCH /{id}/status: обычный пользователь — 403")
    void adminEndpoints_forbiddenForUser() throws Exception {
        UUID userId = UUID.randomUUID();
        String accountId = openAccount(userId);

        mockMvc.perform(get(ACCOUNTS + "/" + accountId).with(user(userId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch(ACCOUNTS + "/" + accountId + "/status").with(user(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountStatus\": \"CLOSED\"}"))
                .andExpect(status().isForbidden());
    }

    private RequestPostProcessor user(UUID userId) {
        return jwt().jwt(jwt -> jwt.subject(userId.toString()));
    }

    private RequestPostProcessor admin() {
        return jwt().jwt(jwt -> jwt.subject(UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private ResultActions changeStatus(String accountId, String status) throws Exception {
        return mockMvc.perform(patch(ACCOUNTS + "/" + accountId + "/status").with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountStatus\": \"%s\"}".formatted(status)));
    }

    private String openAccount(UUID userId) throws Exception {
        String body = mockMvc.perform(post(ACCOUNTS).with(user(userId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private UUID insertPayment(UUID accountId, String status) {
        UUID paymentId = UUID.randomUUID();
        jdbc.update("""
                insert into payments (id, account_id, amount, status, provider, idempotency_key)
                values (?, ?, 500.00, ?, 'TEST', ?)
                """, paymentId, accountId, status, UUID.randomUUID().toString());
        return paymentId;
    }

    private void insertTransaction(UUID accountId, String type, String amount, String balanceAfter,
                                   UUID paymentId, OffsetDateTime createdAt) {
        jdbc.update("""
                insert into transactions (id, account_id, type, amount, balance_after, payment_id, created_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), accountId, type, new BigDecimal(amount), new BigDecimal(balanceAfter),
                paymentId, createdAt);
    }
}

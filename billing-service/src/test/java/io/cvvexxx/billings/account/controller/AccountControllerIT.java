package io.cvvexxx.billings.account.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
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
    @DisplayName("POST: первый вызов открывает счёт — 201, нулевой баланс, номер из 10 цифр")
    void openAccount_firstCall_returns201() throws Exception {
        mockMvc.perform(post(ACCOUNTS).with(user(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.number", matchesPattern("\\d{10}")))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.currency").value("RUB"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST: повторный вызов идемпотентен — 200 и тот же самый счёт")
    void openAccount_secondCall_returns200WithSameAccount() throws Exception {
        UUID userId = UUID.randomUUID();
        String first = mockMvc.perform(post(ACCOUNTS).with(user(userId)))
                .andExpect(status().isCreated())
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
        UUID paymentId = insertPayment(accountId);
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
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Bearer")));
    }

    @Test
    @DisplayName("Битый токен — 401 с error=\"invalid_token\"")
    void malformedToken_returns401InvalidToken() throws Exception {
        mockMvc.perform(get(ACCOUNTS + "/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE,
                        containsString("error=\"invalid_token\"")));
    }

    private RequestPostProcessor user(UUID userId) {
        return jwt().jwt(jwt -> jwt.subject(userId.toString()));
    }

    private String openAccount(UUID userId) throws Exception {
        String body = mockMvc.perform(post(ACCOUNTS).with(user(userId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private UUID insertPayment(UUID accountId) {
        UUID paymentId = UUID.randomUUID();
        jdbc.update("""
                insert into payments (id, account_id, amount, status, provider, idempotency_key)
                values (?, ?, 500.00, 'SUCCEEDED', 'TEST', ?)
                """, paymentId, accountId, UUID.randomUUID().toString());
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

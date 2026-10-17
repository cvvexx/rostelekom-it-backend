package io.cvvexxx.users.auth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cvvexxx.users.otp.store.InMemoryOtpStore;
import io.cvvexxx.users.otp.store.OtpStore;
import io.cvvexxx.users.token.repository.RefreshTokenRepository;
import io.cvvexxx.users.user.entity.Role;
import io.cvvexxx.users.user.entity.User;
import io.cvvexxx.users.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PhoneAuthFlowIT {

    private static final String PHONE = "+79991234567";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private OtpStore otpStore;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        ((InMemoryOtpStore) otpStore).clear();
    }

    @Test
    @DisplayName("Первый вход по номеру: код -> токены -> пользователь создан")
    void firstLogin_createsUserAndIssuesTokens() throws Exception {
        String code = requestOtp("8 (999) 123-45-67").get("code").asText();

        JsonNode tokens = verifyOtp(PHONE, code, status().isOk());

        assertThat(tokens.get("accessToken").asText()).isNotBlank();
        assertThat(tokens.get("refreshToken").asText()).isNotBlank();
        assertThat(tokens.get("tokenType").asText()).isEqualTo("Bearer");

        assertThat(userRepository.count()).isEqualTo(1);
        User saved = userRepository.findByPhone(PHONE).orElseThrow();
        assertThat(saved.getId().toString()).isEqualTo(tokens.get("userId").asText());
        assertThat(saved.getUsername()).isNull();
        assertThat(saved.getRoles()).containsExactly(Role.USER);
    }

    @Test
    @DisplayName("Повторный вход по тому же номеру: нового пользователя не создаём")
    void secondLogin_reusesExistingUser() throws Exception {
        JsonNode first = verifyOtp(PHONE, requestOtp(PHONE).get("code").asText(), status().isOk());

        ((InMemoryOtpStore) otpStore).clear();
        JsonNode second = verifyOtp(PHONE, requestOtp(PHONE).get("code").asText(), status().isOk());

        assertThat(second.get("userId").asText()).isEqualTo(first.get("userId").asText());
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Выданный access-токен открывает доступ к защищённым ручкам")
    void issuedAccessToken_grantsAccessToProtectedEndpoints() throws Exception {
        String accessToken = verifyOtp(PHONE, requestOtp(PHONE).get("code").asText(), status().isOk())
                .get("accessToken").asText();

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value(PHONE))
                .andExpect(jsonPath("$.username").doesNotExist());

        mockMvc.perform(patch("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": " JohnDoe ", "firstName": " John ", "lastName": " Doe "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("johndoe"))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"));
    }

    @Test
    @DisplayName("Роли пользователя уезжают в claim roles списком")
    void issuedAccessToken_carriesRolesClaim() throws Exception {
        String accessToken = verifyOtp(PHONE, requestOtp(PHONE).get("code").asText(), status().isOk())
                .get("accessToken").asText();

        JsonNode payload = decodePayload(accessToken);

        assertThat(payload.get("roles").isArray()).isTrue();
        assertThat(payload.get("roles")).singleElement()
                .extracting(JsonNode::asText).isEqualTo("USER");
    }

    @Test
    @DisplayName("Без токена: 401 с телом в том же формате ProblemDetail, что и прочие ошибки")
    void protectedEndpoint_withoutToken_returns401WithProblemDetail() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/api/users/me"));
    }

    @Test
    @DisplayName("Битый токен: 401 с телом ProblemDetail и сохранённым WWW-Authenticate")
    void protectedEndpoint_withBrokenToken_returns401WithProblemDetailAndHeader() throws Exception {
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE,
                        containsString("error=\"invalid_token\"")));
    }

    @Test
    @DisplayName("Неверный код — 400 с причиной MISMATCH и числом оставшихся попыток")
    void wrongCode_returns400WithReason() throws Exception {
        String code = requestOtp(PHONE).get("code").asText();
        String wrong = code.equals("000000") ? "111111" : "000000";

        mockMvc.perform(post("/api/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone": "%s", "code": "%s"}
                                """.formatted(PHONE, wrong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("MISMATCH"))
                .andExpect(jsonPath("$.attemptsLeft").value(2));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("Повторный запрос кода до истечения кулдауна — 429 с Retry-After")
    void repeatedOtpRequest_returns429() throws Exception {
        requestOtp(PHONE);

        mockMvc.perform(post("/api/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone": "%s"}
                                """.formatted(PHONE)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    @DisplayName("Некорректный номер телефона — 400, код не выдаётся")
    void invalidPhone_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone": "12345"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("refresh: выдаётся новая пара, старый refresh-токен отзывается")
    void refresh_rotatesTokens() throws Exception {
        JsonNode tokens = verifyOtp(PHONE, requestOtp(PHONE).get("code").asText(), status().isOk());
        String refreshToken = tokens.get("refreshToken").asText();

        JsonNode refreshed = objectMapper.readTree(mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(refreshed.get("refreshToken").asText()).isNotEqualTo(refreshToken);
        assertThat(refreshed.get("userId").asText()).isEqualTo(tokens.get("userId").asText());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("logout: refresh-токен отзывается и обменять его больше нельзя")
    void logout_revokesRefreshToken() throws Exception {
        String refreshToken = verifyOtp(PHONE, requestOtp(PHONE).get("code").asText(), status().isOk())
                .get("refreshToken").asText();

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("JWKS-ручка доступна без токена и отдаёт только публичную часть ключа")
    void jwks_isPublicAndContainsNoPrivateKeyMaterial() throws Exception {
        String body = mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].kid").exists())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("\"d\"");
    }

    private static JsonNode decodePayload(String accessToken) throws Exception {
        String payload = accessToken.split("\\.")[1];
        return objectMapper.readTree(Base64.getUrlDecoder().decode(payload));
    }

    private JsonNode requestOtp(String phone) throws Exception {
        String body = mockMvc.perform(post("/api/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone": "%s"}
                                """.formatted(phone)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body);
    }

    private JsonNode verifyOtp(String phone, String code, ResultMatcher expectedStatus) throws Exception {
        String body = mockMvc.perform(post("/api/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone": "%s", "code": "%s"}
                                """.formatted(phone, code)))
                .andExpect(expectedStatus)
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body);
    }
}

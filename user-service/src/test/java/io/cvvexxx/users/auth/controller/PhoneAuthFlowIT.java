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

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    @DisplayName("Новый номер: код -> 202 с токеном регистрации -> /register -> токены, юзер создан")
    void firstLogin_createsUserAndIssuesTokens() throws Exception {
        String code = requestOtp("8 (999) 123-45-67").get("code").asText();

        JsonNode registration = verifyOtp(PHONE, code, status().isAccepted());
        assertThat(registration.get("registrationToken").asText()).isNotBlank();
        assertThat(registration.get("expiresInSeconds").asLong()).isEqualTo(600);
        assertThat(registration.has("accessToken")).isFalse();
        assertThat(userRepository.count()).isZero();//До /register юзера ещё нет

        JsonNode tokens = register(registration.get("registrationToken").asText(), status().isCreated());

        assertThat(tokens.get("accessToken").asText()).isNotBlank();
        assertThat(tokens.get("refreshToken").asText()).isNotBlank();
        assertThat(tokens.get("tokenType").asText()).isEqualTo("Bearer");

        assertThat(userRepository.count()).isEqualTo(1);
        User saved = userRepository.findByPhone(PHONE).orElseThrow();
        assertThat(saved.getId().toString()).isEqualTo(tokens.get("userId").asText());
        assertThat(saved.getRoles()).containsExactly(Role.USER);
        assertThat(saved.getLastName()).isEqualTo("Иванов");//Пробелы по краям обрезаются
        assertThat(saved.getFirstName()).isEqualTo("Иван");
        assertThat(saved.getMiddleName()).isEqualTo("Иванович");
    }

    @Test
    @DisplayName("Регистрация без имени — 400, токен регистрации при этом не сгорает")
    void registration_requiresFullName() throws Exception {
        String registrationToken = registrationToken(PHONE);

        JsonNode error = registerJson("""
                {"registrationToken": "%s", "lastName": "Иванов"}
                """.formatted(registrationToken), status().isBadRequest());
        assertThat(error.at("/errors/firstName").asText()).isEqualTo("Имя обязательно");
        assertThat(userRepository.count()).isZero();

        register(registrationToken, status().isCreated());//Тот же токен всё ещё валиден
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Токен регистрации одноразовый: второй /register с ним — 400")
    void registrationToken_isSingleUse() throws Exception {
        String registrationToken = registrationToken(PHONE);
        register(registrationToken, status().isCreated());

        JsonNode error = register(registrationToken, status().isBadRequest());

        assertThat(error.get("reason").asText()).isEqualTo("NOT_REQUESTED");
        assertThat(error.get("detail").asText())
                .isEqualTo("Сессия регистрации истекла или недействительна, запросите код заново");
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Выдуманный токен регистрации — 400, юзер не создан")
    void register_unknownToken_returns400() throws Exception {
        register("not-a-real-token", status().isBadRequest());

        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("Без правильного кода нельзя узнать, зарегистрирован ли номер: ответы одинаковые")
    void registeredAndNewPhone_lookTheSameWithoutCode() throws Exception {
        String newPhone = "+79997654321";
        login(PHONE);//PHONE зарегистрирован, newPhone - нет
        ((InMemoryOtpStore) otpStore).clear();//Сбрасываем resend-cooldown

        JsonNode registeredChallenge = requestOtp(PHONE);
        JsonNode newChallenge = requestOtp(newPhone);
        assertThat(registeredChallenge.has("registered")).isFalse();
        assertThat(fieldNames(registeredChallenge)).isEqualTo(fieldNames(newChallenge));

        JsonNode registeredError = verifyOtp(PHONE, wrongCode(registeredChallenge), status().isBadRequest());
        JsonNode newError = verifyOtp(newPhone, wrongCode(newChallenge), status().isBadRequest());
        assertThat(registeredError.get("reason")).isEqualTo(newError.get("reason"));
        assertThat(registeredError.get("detail")).isEqualTo(newError.get("detail"));
        assertThat(registeredError.get("attemptsLeft")).isEqualTo(newError.get("attemptsLeft"));
    }

    @Test
    @DisplayName("Повторный вход по тому же номеру: сразу 200 с токенами, нового пользователя не создаём")
    void secondLogin_reusesExistingUser() throws Exception {
        JsonNode first = login(PHONE);

        ((InMemoryOtpStore) otpStore).clear();
        JsonNode second = verifyOtp(PHONE, requestOtp(PHONE).get("code").asText(), status().isOk());

        assertThat(second.get("userId").asText()).isEqualTo(first.get("userId").asText());
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Выданный access-токен открывает доступ к защищённым ручкам")
    void issuedAccessToken_grantsAccessToProtectedEndpoints() throws Exception {
        String accessToken = login(PHONE).get("accessToken").asText();

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value(PHONE))
                .andExpect(jsonPath("$.lastName").value("Иванов"))
                .andExpect(jsonPath("$.firstName").value("Иван"))
                .andExpect(jsonPath("$.middleName").value("Иванович"))
                .andExpect(jsonPath("$.username").doesNotExist());
    }

    @Test
    @DisplayName("Роли пользователя уезжают в claim roles списком")
    void issuedAccessToken_carriesRolesClaim() throws Exception {
        String accessToken = login(PHONE).get("accessToken").asText();

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
                .andExpect(jsonPath("$.detail").value("Access-токен отсутствует, истёк или недействителен"))
                .andExpect(jsonPath("$.instance").value("/api/users/me"));
    }

    @Test
    @DisplayName("Битый токен: 401 с телом ProblemDetail и сохранённым WWW-Authenticate")
    void protectedEndpoint_withBrokenToken_returns401WithProblemDetailAndHeader() throws Exception {
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Access-токен отсутствует, истёк или недействителен"))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE,
                        containsString("error=\"invalid_token\"")));
    }

    @Test
    @DisplayName("Неверный код — 400 с причиной MISMATCH и числом оставшихся попыток")
    void wrongCode_returns400WithReason() throws Exception {
        JsonNode challenge = requestOtp(PHONE);

        mockMvc.perform(post("/api/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone": "%s", "code": "%s"}
                                """.formatted(PHONE, wrongCode(challenge))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Неверный код"))
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
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.detail").value(matchesPattern("Код уже отправлен, повторите через \\d+ с")));
    }

    @Test
    @DisplayName("Некорректный номер телефона — 400, код не выдаётся")
    void invalidPhone_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone": "12345"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Неподдерживаемый формат номера телефона: 12345"));
    }

    @Test
    @DisplayName("Регистрация: имя с цифрами — 400 с текстом из messages.properties, пользователь не создан")
    void registration_nameWithDigits_returns400() throws Exception {
        JsonNode error = registerJson("""
                {"registrationToken": "%s", "lastName": "Иванов", "firstName": "Иван2"}
                """.formatted(registrationToken(PHONE)), status().isBadRequest());

        assertThat(error.at("/errors/firstName").asText())
                .isEqualTo("Имя может содержать только буквы, пробел, дефис и апостроф");
        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("Пустой телефон — 400 с текстом ошибки валидации из messages.properties")
    void blankPhone_returnsLocalizedValidationMessage() throws Exception {
        mockMvc.perform(post("/api/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.phone").value("Номер телефона обязателен"));
    }

    @Test
    @DisplayName("refresh: выдаётся новая пара, старый refresh-токен отзывается")
    void refresh_rotatesTokens() throws Exception {
        JsonNode tokens = login(PHONE);
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
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Refresh-токен отозван"));
    }

    @Test
    @DisplayName("logout: refresh-токен отзывается и обменять его больше нельзя")
    void logout_revokesRefreshToken() throws Exception {
        String refreshToken = login(PHONE).get("refreshToken").asText();

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
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Refresh-токен отозван"));
    }

    @Test
    @DisplayName("Неизвестный refresh-токен — 401 с текстом ошибки")
    void refresh_unknownToken_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "unknown"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Неизвестный refresh-токен"));
    }

    @Test
    @DisplayName("Проверка кода, который не запрашивали — 400 NOT_REQUESTED")
    void verify_withoutRequest_returns400NotRequested() throws Exception {
        mockMvc.perform(post("/api/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone": "%s", "code": "123456"}
                                """.formatted(PHONE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("NOT_REQUESTED"))
                .andExpect(jsonPath("$.detail").value("Код не запрашивался или истёк"));
    }

    @Test
    @DisplayName("Ошибки валидации входа, регистрации и refresh отдаются текстом из messages.properties")
    void validationMessages_areResolved() throws Exception {
        mockMvc.perform(post("/api/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Ошибка валидации"))
                .andExpect(jsonPath("$.errors.phone").value("Номер телефона обязателен"))
                .andExpect(jsonPath("$.errors.code").value("Код обязателен"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.refreshToken").value("Refresh-токен обязателен"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.registrationToken").value("Токен регистрации обязателен"))
                .andExpect(jsonPath("$.errors.lastName").value("Фамилия обязательна"))
                .andExpect(jsonPath("$.errors.firstName").value("Имя обязательно"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"registrationToken": "token", "lastName": "Иванов", "firstName": "%s"}
                                """.formatted("x".repeat(101))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.firstName").value("Имя слишком длинное"));
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

    //Код -> verify для нового номера -> токен регистрации
    private String registrationToken(String phone) throws Exception {
        return verifyOtp(phone, requestOtp(phone).get("code").asText(), status().isAccepted())
                .get("registrationToken").asText();
    }

    //Полный путь нового юзера: код -> токен регистрации -> /register с ФИО -> токены
    private JsonNode login(String phone) throws Exception {
        return register(registrationToken(phone), status().isCreated());
    }

    //ФИО с пробелами по краям, чтобы проверить, что они обрезаются при сохранении
    private JsonNode register(String registrationToken, ResultMatcher expectedStatus) throws Exception {
        return registerJson("""
                {"registrationToken": "%s", "lastName": " Иванов ", "firstName": "Иван", "middleName": "Иванович"}
                """.formatted(registrationToken), expectedStatus);
    }

    private JsonNode registerJson(String json, ResultMatcher expectedStatus) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(expectedStatus)
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body);
    }

    private static String wrongCode(JsonNode challenge) {
        return challenge.get("code").asText().equals("000000") ? "111111" : "000000";
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}

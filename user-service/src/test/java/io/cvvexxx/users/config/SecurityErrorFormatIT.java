package io.cvvexxx.users.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cvvexxx.users.otp.store.InMemoryOtpStore;
import io.cvvexxx.users.otp.store.OtpStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityErrorFormatIT {

    private static final String PHONE = "+79990001122";

    @TestConfiguration
    @EnableMethodSecurity
    static class ForbiddenEndpoints {

        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        SecurityFilterChain urlDeniedChain(HttpSecurity http,
                                           AuthenticationEntryPoint authenticationEntryPoint,
                                           AccessDeniedHandler accessDeniedHandler,
                                           JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
            return http
                    .securityMatcher("/test-url-denied/**")
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(request -> request.anyRequest().hasRole("ADMIN"))
                    .oauth2ResourceServer(oauth2 -> oauth2
                            .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                            .authenticationEntryPoint(authenticationEntryPoint)
                            .accessDeniedHandler(accessDeniedHandler))
                    .exceptionHandling(exceptions -> exceptions
                            .authenticationEntryPoint(authenticationEntryPoint)
                            .accessDeniedHandler(accessDeniedHandler))
                    .build();
        }

        @RestController
        static class ForbiddenController {

            @GetMapping("/test-url-denied/admin")
            String deniedByUrlRule() {
                return "secret";
            }

            @GetMapping("/test-method-denied/admin")
            @PreAuthorize("hasRole('ADMIN')")
            String deniedByMethodRule() {
                return "secret";
            }

            @GetMapping("/test-unexpected-error")
            String unexpectedError() {
                throw new IllegalStateException("секретная внутренняя деталь");
            }
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OtpStore otpStore;

    @BeforeEach
    void setUp() {
        ((InMemoryOtpStore) otpStore).clear();
    }

    @Test
    @DisplayName("Запрет по URL-правилу: 403 в формате ProblemDetail, заголовок RFC 6750 на месте")
    void urlRuleDenial_returns403WithProblemDetail() throws Exception {
        mockMvc.perform(get("/test-url-denied/admin")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.detail").value("Недостаточно прав для доступа к ресурсу"))
                .andExpect(jsonPath("$.instance").value("/test-url-denied/admin"))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE,
                        containsString("error=\"insufficient_scope\"")));
    }

    @Test
    @DisplayName("Запрет по @PreAuthorize: то же самое тело, хотя путь до него другой")
    void methodRuleDenial_returns403WithSameProblemDetail() throws Exception {
        mockMvc.perform(get("/test-method-denied/admin")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.detail").value("Недостаточно прав для доступа к ресурсу"));
    }

    @Test
    @DisplayName("Без токена на той же ручке — 401, а не 403")
    void urlRuleWithoutToken_returns401() throws Exception {
        mockMvc.perform(get("/test-url-denied/admin"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Access-токен отсутствует, истёк или недействителен"));
    }

    @Test
    @DisplayName("Битый JSON в теле — 400 в том же ProblemDetail, а не в дефолтном формате Spring Boot")
    void malformedJson_returnsProblemDetail() throws Exception {
        mockMvc.perform(post("/api/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.instance").value("/api/auth/otp/request"))
                .andExpect(jsonPath("$.timestamp").doesNotExist());
    }

    @Test
    @DisplayName("Непойманное исключение — 500 в ProblemDetail без внутренних деталей")
    void unexpectedException_returns500WithoutDetails() throws Exception {
        mockMvc.perform(get("/test-unexpected-error")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value("Внутренняя ошибка сервера, попробуйте позже"));
    }

    private String accessToken() throws Exception {
        String challenge = mockMvc.perform(post("/api/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone": "%s"}
                                """.formatted(PHONE)))
                .andReturn().getResponse().getContentAsString();
        String code = new ObjectMapper().readTree(challenge).get("code").asText();

        String tokens = mockMvc.perform(post("/api/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone": "%s", "code": "%s"}
                                """.formatted(PHONE, code)))
                .andReturn().getResponse().getContentAsString();

        return new ObjectMapper().readTree(tokens).get("accessToken").asText();
    }
}

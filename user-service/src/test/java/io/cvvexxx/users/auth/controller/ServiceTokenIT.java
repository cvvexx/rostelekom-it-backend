package io.cvvexxx.users.auth.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ServiceTokenIT {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("Верный секрет: выдаём подписанный токен с ролью SERVICE")
    void validCredentials_issuesServiceToken() throws Exception {
        String body = request("test-service", "test-service-secret", status().isOk());

        Jwt jwt = jwtDecoder.decode(objectMapper.readTree(body).get("accessToken").asText());
        assertThat(jwt.getSubject()).isEqualTo("service:test-service");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("SERVICE");
        assertThat(objectMapper.readTree(body).get("expiresInSeconds").asLong()).isEqualTo(900);
    }

    @Test
    @DisplayName("Неверный секрет, неизвестный клиент или клиент без секрета: 401")
    void invalidCredentials_returns401() throws Exception {
        request("billing-service", "wrong", status().isUnauthorized());
        request("unknown-service", "test-service-secret", status().isUnauthorized());
        request("unconfigured-service", " x", status().isUnauthorized());
    }

    private String request(String clientId, String clientSecret, ResultMatcher expected) throws Exception {
        return mockMvc.perform(post("/api/auth/service-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("clientId", clientId, "clientSecret", clientSecret))))
                .andExpect(expected)
                .andReturn().getResponse().getContentAsString();
    }
}

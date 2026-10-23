package io.cvvexxx.gatewayservice.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

//user-service направлен на заведомо закрытый порт: запрос, прошедший security, упирается в CircuitBreaker
//и уходит forward на fallback (MockMvc сам forward не выполняет, только запоминает адрес)
@SpringBootTest(properties = "USER_SERVICE_URI=http://localhost:1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class GatewaySecurityIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Защищённый маршрут без токена отклоняется на gateway с 401")
    void protectedRouteWithoutToken() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Access-токен отсутствует, истёк или недействителен"));
    }

    @Test
    @DisplayName("Защищённый маршрут с невалидным токеном отклоняется на gateway с 401")
    void protectedRouteWithMalformedToken() throws Exception {
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("invalid_token")))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Защищённый маршрут с валидным токеном проходит дальше к user-service")
    void protectedRouteWithToken() throws Exception {
        mockMvc.perform(get("/api/users/me").with(jwt()))
                .andExpect(forwardedUrl("/fallback/users"));
    }

    @Test
    @DisplayName("Маршрут входа по телефону доступен без токена")
    void authRouteIsPublic() throws Exception {
        mockMvc.perform(post("/api/auth/otp"))
                .andExpect(forwardedUrl("/fallback/users"));
    }
}

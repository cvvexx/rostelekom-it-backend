package io.cvvexxx.gatewayservice.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

//Вместо Redis мок: он отдаёт номер запроса в текущем окне, по нему фильтр и решает
@SpringBootTest(properties = {"USER_SERVICE_URI=http://localhost:1", "rostelekom.rate-limit.capacity=2"})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class RateLimitIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StringRedisTemplate redis;

    @Test
    @DisplayName("Запрос в пределах лимита проходит дальше")
    void withinLimit() throws Exception {
        when(redis.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(2L);

        mockMvc.perform(post("/api/auth/otp"))
                .andExpect(forwardedUrl("/fallback/users"));
    }

    @Test
    @DisplayName("Сверх лимита gateway отвечает 429 в ProblemDetail")
    void overLimit() throws Exception {
        when(redis.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(3L);

        mockMvc.perform(post("/api/auth/otp"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.detail").value("Слишком много запросов, попробуйте позже"));
    }
}

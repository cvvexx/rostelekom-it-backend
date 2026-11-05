package io.cvvexxx.services.subscription.controller;

import io.cvvexxx.services.advice.GlobalExceptionHandler;
import io.cvvexxx.services.config.SecurityBeans;
import io.cvvexxx.services.subscription.dto.SubscriptionDto;
import io.cvvexxx.services.subscription.entity.SubscriptionStatus;
import io.cvvexxx.services.subscription.exception.ChargeDeclinedException;
import io.cvvexxx.services.subscription.exception.SubscriptionNotFoundException;
import io.cvvexxx.services.subscription.service.SubscriptionService;
import io.cvvexxx.services.tariff.entity.BillingPeriod;
import io.cvvexxx.services.tariff.entity.TariffCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SubscriptionController.class)
@ActiveProfiles("test")
@Import({SecurityBeans.class, GlobalExceptionHandler.class})
class SubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubscriptionService subscriptionService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private final UUID userId = UUID.randomUUID();
    private final UUID tariffId = UUID.randomUUID();
    private final UUID subscriptionId = UUID.randomUUID();

    private RequestPostProcessor user() {
        return jwt().jwt(j -> j.subject(userId.toString())).authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private RequestPostProcessor admin() {
        return jwt().jwt(j -> j.subject(userId.toString())).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private SubscriptionDto sampleDto() {
        return new SubscriptionDto(
                subscriptionId,
                userId,
                tariffId,
                "Интернет 500",
                TariffCategory.INTERNET,
                SubscriptionStatus.ACTIVE,
                new BigDecimal("500.00"),
                "RUB",
                BillingPeriod.MONTH,
                Instant.parse("2026-11-01T12:00:00Z"),
                Instant.parse("2026-12-01T12:00:00Z"),
                false,
                null,
                UUID.randomUUID(),
                Instant.parse("2026-11-01T12:00:00Z")
        );
    }

    @Test
    @DisplayName("Без токена все эндпоинты подписок возвращают 401 Unauthorized")
    void withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/services/subscriptions/my"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/services/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tariffId\": \"%s\"}".formatted(tariffId)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(subscriptionService);
    }

    @Test
    @DisplayName("POST /subscriptions подписывает пользователя из sub токена и возвращает 201 Created")
    void subscribe_returnsCreated() throws Exception {
        when(subscriptionService.subscribe(userId, tariffId)).thenReturn(sampleDto());

        mockMvc.perform(post("/api/services/subscriptions")
                        .with(user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tariffId\": \"%s\"}".formatted(tariffId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(subscriptionId.toString()))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /subscriptions без tariffId возвращает 400 и ошибку валидации")
    void subscribe_withoutTariffId_returnsValidationError() throws Exception {
        mockMvc.perform(post("/api/services/subscriptions")
                        .with(user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("error.validation-failed"))
                .andExpect(jsonPath("$.errors.tariffId").exists());

        verifyNoInteractions(subscriptionService);
    }

    @Test
    @DisplayName("POST /subscriptions при отказе биллинга возвращает 409 с кодом отказа")
    void subscribe_chargeDeclined_returnsConflict() throws Exception {
        when(subscriptionService.subscribe(userId, tariffId))
                .thenThrow(new ChargeDeclinedException("charge.insufficient-funds", userId));

        mockMvc.perform(post("/api/services/subscriptions")
                        .with(user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tariffId\": \"%s\"}".formatted(tariffId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("charge.insufficient-funds"));
    }

    @Test
    @DisplayName("GET /subscriptions/my возвращает страницу подписок текущего пользователя")
    void getMySubscriptions_returnsPage() throws Exception {
        when(subscriptionService.getMySubscriptions(eq(userId), any())).thenReturn(new PageImpl<>(List.of(sampleDto())));

        mockMvc.perform(get("/api/services/subscriptions/my").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(subscriptionId.toString()))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /subscriptions/my с запрещённым полем сортировки возвращает 400 Bad Request")
    void getMySubscriptions_invalidSort_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/services/subscriptions/my").with(user()).param("sort", "userId,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("error.invalid-sort"));

        verifyNoInteractions(subscriptionService);
    }

    @Test
    @DisplayName("GET /subscriptions/{id} для USER передаёт isAdmin=false")
    void getSubscription_asUser_passesNotAdmin() throws Exception {
        when(subscriptionService.getSubscription(userId, subscriptionId, false)).thenReturn(sampleDto());

        mockMvc.perform(get("/api/services/subscriptions/{id}", subscriptionId).with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(subscriptionId.toString()));
    }

    @Test
    @DisplayName("GET /subscriptions/{id} для ADMIN передаёт isAdmin=true")
    void getSubscription_asAdmin_passesAdmin() throws Exception {
        when(subscriptionService.getSubscription(userId, subscriptionId, true)).thenReturn(sampleDto());

        mockMvc.perform(get("/api/services/subscriptions/{id}", subscriptionId).with(admin()))
                .andExpect(status().isOk());

        verify(subscriptionService).getSubscription(userId, subscriptionId, true);
    }

    @Test
    @DisplayName("GET /subscriptions/{id} несуществующей подписки возвращает 404")
    void getSubscription_notFound_returnsNotFound() throws Exception {
        when(subscriptionService.getSubscription(userId, subscriptionId, false))
                .thenThrow(new SubscriptionNotFoundException("subscription.not-found", subscriptionId));

        mockMvc.perform(get("/api/services/subscriptions/{id}", subscriptionId).with(user()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("subscription.not-found"));
    }

    @Test
    @DisplayName("POST /subscriptions/{id}/cancel по умолчанию отменяет в конце периода (immediate=false)")
    void cancel_defaultIsNotImmediate() throws Exception {
        when(subscriptionService.cancelSubscription(userId, subscriptionId, false, false)).thenReturn(sampleDto());

        mockMvc.perform(post("/api/services/subscriptions/{id}/cancel", subscriptionId).with(user()))
                .andExpect(status().isOk());

        verify(subscriptionService).cancelSubscription(userId, subscriptionId, false, false);
    }

    @Test
    @DisplayName("POST /subscriptions/{id}/cancel?immediate=true передаёт immediate=true")
    void cancel_immediate() throws Exception {
        when(subscriptionService.cancelSubscription(userId, subscriptionId, true, true)).thenReturn(sampleDto());

        mockMvc.perform(post("/api/services/subscriptions/{id}/cancel", subscriptionId)
                        .with(admin())
                        .param("immediate", "true"))
                .andExpect(status().isOk());

        verify(subscriptionService).cancelSubscription(userId, subscriptionId, true, true);
    }
}

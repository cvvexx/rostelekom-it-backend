package io.cvvexxx.services.tariff.controller;

import io.cvvexxx.services.advice.GlobalExceptionHandler;
import io.cvvexxx.services.config.SecurityBeans;
import io.cvvexxx.services.tariff.dto.TariffDto;
import io.cvvexxx.services.tariff.entity.BillingPeriod;
import io.cvvexxx.services.tariff.entity.TariffCategory;
import io.cvvexxx.services.tariff.service.TariffService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TariffController.class)
@ActiveProfiles("test")
@Import({SecurityBeans.class, GlobalExceptionHandler.class})
class TariffControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TariffService tariffService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static TariffDto sampleDto() {
        return new TariffDto(
                UUID.randomUUID(),
                "Интернет 500",
                "Описание",
                TariffCategory.INTERNET,
                new BigDecimal("499.00"),
                "RUB",
                BillingPeriod.MONTH,
                true,
                Instant.parse("2026-11-01T12:00:00Z")
        );
    }

    @Test
    @DisplayName("GET /api/services/tariffs доступен без авторизации")
    void getActiveTariffs_publicAccess() throws Exception {
        TariffDto dto = sampleDto();
        when(tariffService.getActiveTariffs(eq(null), any())).thenReturn(new PageImpl<>(List.of(dto)));

        mockMvc.perform(get("/api/services/tariffs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Интернет 500"));
    }

    @Test
    @DisplayName("GET /api/services/tariffs с запрещённым полем сортировки возвращает 400 Bad Request")
    void getActiveTariffs_invalidSort_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/services/tariffs").param("sort", "unknownField,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("error.invalid-sort"));
    }

    @Test
    @DisplayName("POST /api/services/tariffs с ролью USER возвращает 403 Forbidden")
    void createTariff_asUser_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/services/tariffs")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Тариф", "category": "INTERNET", "price": 500.00}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/services/tariffs с ролью ADMIN создаёт тариф и возвращает 201 Created")
    void createTariff_asAdmin_returnsCreated() throws Exception {
        TariffDto dto = sampleDto();
        when(tariffService.createTariff(any())).thenReturn(dto);
        mockMvc.perform(post("/api/services/tariffs")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Интернет 500", "category": "INTERNET", "price": 499.00}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(dto.id().toString()))
                .andExpect(jsonPath("$.name").value("Интернет 500"));
    }

    @Test
    @DisplayName("POST /api/services/tariffs с пустым именем и отрицательной ценой возвращает 400 и ошибки валидации")
    void createTariff_invalidBody_returnsValidationErrors() throws Exception {
        mockMvc.perform(post("/api/services/tariffs")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "   ", "category": "INTERNET", "price": -10.00}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("error.validation-failed"))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.price").exists());
    }
}
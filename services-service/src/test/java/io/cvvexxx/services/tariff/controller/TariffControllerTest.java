package io.cvvexxx.services.tariff.controller;

import io.cvvexxx.services.advice.GlobalExceptionHandler;
import io.cvvexxx.services.config.SecurityBeans;
import io.cvvexxx.services.tariff.dto.TariffDto;
import io.cvvexxx.services.tariff.entity.BillingPeriod;
import io.cvvexxx.services.tariff.entity.TariffCategory;
import io.cvvexxx.services.tariff.exception.TariffAlreadyExistsException;
import io.cvvexxx.services.tariff.exception.TariffNotFoundException;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    private static final SimpleGrantedAuthority ADMIN = new SimpleGrantedAuthority("ROLE_ADMIN");
    private static final SimpleGrantedAuthority USER = new SimpleGrantedAuthority("ROLE_USER");

    @Test
    @DisplayName("GET /api/services/tariffs?category=TV передаёт категорию в сервис")
    void getActiveTariffs_withCategory() throws Exception {
        when(tariffService.getActiveTariffs(eq(TariffCategory.TV), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/services/tariffs").param("category", "TV"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        verify(tariffService).getActiveTariffs(eq(TariffCategory.TV), any());
    }

    @Test
    @DisplayName("GET /api/services/tariffs/all: без токена 401, USER 403, ADMIN 200")
    void getAllTariffs_access() throws Exception {
        when(tariffService.getAllTariffs(eq(null), any())).thenReturn(new PageImpl<>(List.of(sampleDto())));

        mockMvc.perform(get("/api/services/tariffs/all"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/services/tariffs/all").with(jwt().authorities(USER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/services/tariffs/all").with(jwt().authorities(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Интернет 500"));
    }

    @Test
    @DisplayName("GET /api/services/tariffs/{id}: аноним и USER получают isAdmin=false, ADMIN — isAdmin=true")
    void getTariff_passesAdminFlag() throws Exception {
        TariffDto dto = sampleDto();
        when(tariffService.getTariffById(dto.id(), false)).thenReturn(dto);
        when(tariffService.getTariffById(dto.id(), true)).thenReturn(dto);

        mockMvc.perform(get("/api/services/tariffs/{id}", dto.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dto.id().toString()));
        mockMvc.perform(get("/api/services/tariffs/{id}", dto.id()).with(jwt().authorities(USER)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/services/tariffs/{id}", dto.id()).with(jwt().authorities(ADMIN)))
                .andExpect(status().isOk());

        verify(tariffService, org.mockito.Mockito.times(2)).getTariffById(dto.id(), false);
        verify(tariffService).getTariffById(dto.id(), true);
    }

    @Test
    @DisplayName("GET /api/services/tariffs/{id} несуществующего тарифа возвращает 404")
    void getTariff_notFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(tariffService.getTariffById(id, false)).thenThrow(new TariffNotFoundException("tariff.not-found", id));

        mockMvc.perform(get("/api/services/tariffs/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("tariff.not-found"));
    }

    @Test
    @DisplayName("POST /api/services/tariffs без токена возвращает 401")
    void createTariff_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/services/tariffs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Тариф", "category": "INTERNET", "price": 500.00}
                                """))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(tariffService);
    }

    @Test
    @DisplayName("POST /api/services/tariffs с занятым именем возвращает 409")
    void createTariff_duplicate_returnsConflict() throws Exception {
        when(tariffService.createTariff(any()))
                .thenThrow(new TariffAlreadyExistsException("tariff.already-exists", "Интернет 500"));

        mockMvc.perform(post("/api/services/tariffs")
                        .with(jwt().authorities(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Интернет 500", "category": "INTERNET", "price": 499.00}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("tariff.already-exists"));
    }

    @Test
    @DisplayName("POST /api/services/tariffs без категории и цены, с ценой из 3 знаков после запятой — 400")
    void createTariff_missingFields_returnsValidationErrors() throws Exception {
        mockMvc.perform(post("/api/services/tariffs")
                        .with(jwt().authorities(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Тариф"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.category").exists())
                .andExpect(jsonPath("$.errors.price").exists());

        mockMvc.perform(post("/api/services/tariffs")
                        .with(jwt().authorities(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Тариф", "category": "TV", "price": 10.123}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.price").exists());

        verifyNoInteractions(tariffService);
    }

    @Test
    @DisplayName("PATCH /api/services/tariffs/{id}: USER 403, ADMIN 200")
    void updateTariff_access() throws Exception {
        TariffDto dto = sampleDto();
        when(tariffService.updateTariff(eq(dto.id()), any())).thenReturn(dto);

        mockMvc.perform(patch("/api/services/tariffs/{id}", dto.id())
                        .with(jwt().authorities(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\": 600.00}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/services/tariffs/{id}", dto.id())
                        .with(jwt().authorities(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\": 600.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dto.id().toString()));
    }

    @Test
    @DisplayName("PATCH /api/services/tariffs/{id} с пустым именем и неположительной ценой возвращает 400")
    void updateTariff_invalidBody_returnsValidationErrors() throws Exception {
        mockMvc.perform(patch("/api/services/tariffs/{id}", UUID.randomUUID())
                        .with(jwt().authorities(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "   ", "price": 0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.price").exists());

        verifyNoInteractions(tariffService);
    }

    @Test
    @DisplayName("DELETE /api/services/tariffs/{id}: USER 403, ADMIN архивирует тариф, несуществующий — 404")
    void archiveTariff() throws Exception {
        TariffDto dto = sampleDto();
        UUID missing = UUID.randomUUID();
        when(tariffService.archiveTariff(dto.id())).thenReturn(dto);
        when(tariffService.archiveTariff(missing)).thenThrow(new TariffNotFoundException("tariff.not-found", missing));

        mockMvc.perform(delete("/api/services/tariffs/{id}", dto.id()).with(jwt().authorities(USER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/services/tariffs/{id}", dto.id()).with(jwt().authorities(ADMIN)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/services/tariffs/{id}", missing).with(jwt().authorities(ADMIN)))
                .andExpect(status().isNotFound());

        verify(tariffService).archiveTariff(dto.id());
    }
}

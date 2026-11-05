package io.cvvexxx.services.tariff.service;

import io.cvvexxx.services.tariff.dto.CreateTariffDto;
import io.cvvexxx.services.tariff.dto.TariffDto;
import io.cvvexxx.services.tariff.dto.UpdateTariffDto;
import io.cvvexxx.services.tariff.entity.BillingPeriod;
import io.cvvexxx.services.tariff.entity.TariffCategory;
import io.cvvexxx.services.tariff.exception.TariffAlreadyExistsException;
import io.cvvexxx.services.tariff.exception.TariffNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@EnabledIfDockerAvailable
class TariffServiceIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private TariffService tariffService;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from subscriptions");
        jdbc.update("delete from tariffs");
    }

    @Test
    @DisplayName("Создание тарифа: обрезает пробелы, выставляет дефолтный период MONTH и сохраняет в БД")
    void createTariff_savesAndTrimsFields() {
        CreateTariffDto dto = new CreateTariffDto(
                "  Интернет 500  ",
                "  Быстрый домашний интернет  ",
                TariffCategory.INTERNET,
                new BigDecimal("499.00"),
                null
        );

        TariffDto created = tariffService.createTariff(dto);

        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo("Интернет 500");
        assertThat(created.description()).isEqualTo("Быстрый домашний интернет");
        assertThat(created.category()).isEqualTo(TariffCategory.INTERNET);
        assertThat(created.price()).isEqualByComparingTo("499.00");
        assertThat(created.currency()).isEqualTo("RUB");
        assertThat(created.billingPeriod()).isEqualTo(BillingPeriod.MONTH);
        assertThat(created.active()).isTrue();
    }

    @Test
    @DisplayName("Создание дубликата по имени: insertIfAbsent возвращает 0 и бросает TariffAlreadyExistsException")
    void createTariff_duplicateName_throwsAlreadyExists() {
        CreateTariffDto dto = new CreateTariffDto(
                "Интернет 500", "Описание", TariffCategory.INTERNET, new BigDecimal("499.00"), BillingPeriod.MONTH
        );
        tariffService.createTariff(dto);

        CreateTariffDto duplicate = new CreateTariffDto(
                "  Интернет 500 ", "Другое описание", TariffCategory.TV, new BigDecimal("999.00"), BillingPeriod.MONTH
        );

        assertThatThrownBy(() -> tariffService.createTariff(duplicate))
                .isInstanceOf(TariffAlreadyExistsException.class)
                .hasMessage("tariff.already-exists");
    }

    @Test
    @DisplayName("Получение тарифов: обычным пользователям возвращаются только активные, админу — все")
    void getTariffs_filtersActiveAndCategory() {
        TariffDto activeInternet = tariffService.createTariff(new CreateTariffDto(
                "Интернет 100", null, TariffCategory.INTERNET, new BigDecimal("300.00"), BillingPeriod.MONTH));
        TariffDto archivedInternet = tariffService.createTariff(new CreateTariffDto(
                "Интернет Архив", null, TariffCategory.INTERNET, new BigDecimal("200.00"), BillingPeriod.MONTH));
        TariffDto activeTv = tariffService.createTariff(new CreateTariffDto(
                "ТВ Кино", null, TariffCategory.TV, new BigDecimal("400.00"), BillingPeriod.MONTH));

        tariffService.archiveTariff(archivedInternet.id());

        Page<TariffDto> activeAll = tariffService.getActiveTariffs(null, PageRequest.of(0, 10));
        assertThat(activeAll.getContent()).extracting(TariffDto::id)
                .containsExactlyInAnyOrder(activeInternet.id(), activeTv.id());

        Page<TariffDto> activeInternetOnly = tariffService.getActiveTariffs(TariffCategory.INTERNET, PageRequest.of(0, 10));
        assertThat(activeInternetOnly.getContent()).extracting(TariffDto::id)
                .containsExactly(activeInternet.id());

        Page<TariffDto> adminAll = tariffService.getAllTariffs(TariffCategory.INTERNET, PageRequest.of(0, 10));
        assertThat(adminAll.getContent()).extracting(TariffDto::id)
                .containsExactlyInAnyOrder(activeInternet.id(), archivedInternet.id());
    }

    @Test
    @DisplayName("Получение по id: архивный тариф виден админу, но кидает TariffNotFoundException для пользователя")
    void getTariffById_archivedVisibility() {
        TariffDto created = tariffService.createTariff(new CreateTariffDto(
                "Мобильный", null, TariffCategory.MOBILE, new BigDecimal("350.00"), BillingPeriod.MONTH));
        tariffService.archiveTariff(created.id());

        assertThatThrownBy(() -> tariffService.getTariffById(created.id(), false))
                .isInstanceOf(TariffNotFoundException.class);

        TariffDto forAdmin = tariffService.getTariffById(created.id(), true);
        assertThat(forAdmin.active()).isFalse();
    }

    @Test
    @DisplayName("Обновление тарифа: меняет переданные поля и запрещает переименование в уже занятое имя")
    void updateTariff_updatesFieldsAndChecksDuplicateName() {
        TariffDto first = tariffService.createTariff(new CreateTariffDto(
                "Тариф 1", "Старое", TariffCategory.INTERNET, new BigDecimal("300.00"), BillingPeriod.MONTH));
        tariffService.createTariff(new CreateTariffDto(
                "Тариф 2", "Второй", TariffCategory.INTERNET, new BigDecimal("400.00"), BillingPeriod.MONTH));

        UpdateTariffDto updateDto = new UpdateTariffDto(
                "  Тариф 1 Обновленный  ",
                "Новое описание",
                TariffCategory.MOBILE,
                new BigDecimal("550.00"),
                false
        );

        TariffDto updated = tariffService.updateTariff(first.id(), updateDto);
        assertThat(updated.name()).isEqualTo("Тариф 1 Обновленный");
        assertThat(updated.description()).isEqualTo("Новое описание");
        assertThat(updated.category()).isEqualTo(TariffCategory.MOBILE);
        assertThat(updated.price()).isEqualByComparingTo("550.00");
        assertThat(updated.active()).isFalse();

        UpdateTariffDto conflictDto = new UpdateTariffDto("Тариф 2", null, null, null, null);
        assertThatThrownBy(() -> tariffService.updateTariff(first.id(), conflictDto))
                .isInstanceOf(TariffAlreadyExistsException.class);
    }

    @Test
    @DisplayName("Частичное обновление: null-поля не трогаются, переименование в своё же имя разрешено")
    void updateTariff_partialAndSameName() {
        TariffDto created = tariffService.createTariff(new CreateTariffDto(
                "Интернет 300", "Описание", TariffCategory.INTERNET, new BigDecimal("300.00"), BillingPeriod.MONTH));

        TariffDto updated = tariffService.updateTariff(created.id(),
                new UpdateTariffDto("Интернет 300", null, null, new BigDecimal("350.00"), null));

        assertThat(updated.name()).isEqualTo("Интернет 300");
        assertThat(updated.description()).isEqualTo("Описание");
        assertThat(updated.category()).isEqualTo(TariffCategory.INTERNET);
        assertThat(updated.price()).isEqualByComparingTo("350.00");
        assertThat(updated.active()).isTrue();
        assertThat(jdbc.queryForObject("select price from tariffs where id = ?", BigDecimal.class, created.id()))
                .isEqualByComparingTo("350.00");
    }

    @Test
    @DisplayName("Архивация: тариф пропадает из активных, но остаётся в БД с active=false")
    void archiveTariff_setsInactive() {
        TariffDto created = tariffService.createTariff(new CreateTariffDto(
                "ТВ Базовый", null, TariffCategory.TV, new BigDecimal("200.00"), BillingPeriod.MONTH));

        TariffDto archived = tariffService.archiveTariff(created.id());

        assertThat(archived.active()).isFalse();
        assertThat(jdbc.queryForObject("select active from tariffs where id = ?", Boolean.class, created.id())).isFalse();
        assertThat(tariffService.getActiveTariffs(null, PageRequest.of(0, 10)).getContent()).isEmpty();
    }

    @Test
    @DisplayName("Обновление и архивация несуществующего тарифа бросают TariffNotFoundException")
    void updateAndArchive_notFound() {
        UUID missing = UUID.randomUUID();

        assertThatThrownBy(() -> tariffService.updateTariff(missing, new UpdateTariffDto(null, null, null, null, null)))
                .isInstanceOf(TariffNotFoundException.class);
        assertThatThrownBy(() -> tariffService.archiveTariff(missing))
                .isInstanceOf(TariffNotFoundException.class);
        assertThatThrownBy(() -> tariffService.getTariffById(missing, true))
                .isInstanceOf(TariffNotFoundException.class);
    }
}

package io.cvvexxx.services.tariff.service;

import io.cvvexxx.services.tariff.dto.CreateTariffDto;
import io.cvvexxx.services.tariff.dto.TariffDto;
import io.cvvexxx.services.tariff.dto.UpdateTariffDto;
import io.cvvexxx.services.tariff.entity.BillingPeriod;
import io.cvvexxx.services.tariff.entity.Tariff;
import io.cvvexxx.services.tariff.entity.TariffCategory;
import io.cvvexxx.services.tariff.exception.TariffNotFoundException;
import io.cvvexxx.services.tariff.repository.TariffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TariffService {

    private final TariffRepository tariffRepository;

    @Transactional(readOnly = true)
    public Page<TariffDto> getActiveTariffs(TariffCategory category, Pageable pageable) {
        Page<Tariff> tariffs = category == null
                ? tariffRepository.findByActiveTrueOrderByCreatedAtDesc(pageable)
                : tariffRepository.findByActiveTrueAndCategoryOrderByCreatedAtDesc(category, pageable);

        return tariffs.map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<TariffDto> getAllTariffs(Pageable pageable) {
        return tariffRepository.findAll(pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public TariffDto getTariffById(UUID id) {
        return toDto(findTariff(id));
    }

    @Transactional
    public TariffDto createTariff(CreateTariffDto dto) {
        Tariff tariff = Tariff.builder()
                .name(dto.name().trim())
                .description(dto.description() != null ? dto.description().trim() : null)
                .category(dto.category())
                .price(dto.price())
                .billingPeriod(dto.billingPeriod() != null ? dto.billingPeriod() : BillingPeriod.MONTH)
                .active(true)
                .build();

        Tariff saved = tariffRepository.save(tariff);
        log.info("Created tariff {} ({}, {} {})", saved.getId(), saved.getName(), saved.getPrice(), saved.getCurrency());
        return toDto(saved);
    }

    @Transactional
    public TariffDto updateTariff(UUID id, UpdateTariffDto dto) {
        Tariff tariff = findTariff(id);

        if (dto.name() != null && !dto.name().isBlank()) {
            tariff.setName(dto.name().trim());
        }
        if (dto.description() != null) {
            tariff.setDescription(dto.description().trim());
        }
        if (dto.category() != null) {
            tariff.setCategory(dto.category());
        }
        if (dto.price() != null) {
            tariff.setPrice(dto.price());
        }
        if (dto.active() != null) {
            tariff.setActive(dto.active());
        }

        return toDto(tariff);
    }

    @Transactional
    public TariffDto archiveTariff(UUID id) {
        Tariff tariff = findTariff(id);
        tariff.setActive(false);
        log.info("Archived tariff {}", id);
        return toDto(tariff);
    }

    public Tariff findTariff(UUID id) {
        return tariffRepository.findById(id)
                .orElseThrow(() -> new TariffNotFoundException("tariff.not-found", id));
    }

    public TariffDto toDto(Tariff tariff) {
        return new TariffDto(
                tariff.getId(),
                tariff.getName(),
                tariff.getDescription(),
                tariff.getCategory(),
                tariff.getPrice(),
                tariff.getCurrency(),
                tariff.getBillingPeriod(),
                tariff.isActive(),
                tariff.getCreatedAt()
        );
    }
}

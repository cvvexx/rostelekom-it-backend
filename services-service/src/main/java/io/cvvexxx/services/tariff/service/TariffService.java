package io.cvvexxx.services.tariff.service;

import io.cvvexxx.services.tariff.dto.CreateTariffDto;
import io.cvvexxx.services.tariff.dto.TariffDto;
import io.cvvexxx.services.tariff.dto.UpdateTariffDto;
import io.cvvexxx.services.tariff.entity.BillingPeriod;
import io.cvvexxx.services.tariff.entity.Tariff;
import io.cvvexxx.services.tariff.entity.TariffCategory;
import io.cvvexxx.services.tariff.exception.TariffAlreadyExistsException;
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
                ? tariffRepository.findByActiveTrue(pageable)
                : tariffRepository.findByActiveTrueAndCategory(category, pageable);

        return tariffs.map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<TariffDto> getAllTariffs(TariffCategory category, Pageable pageable) {
        Page<Tariff> tariffs = category == null
                ? tariffRepository.findAll(pageable)
                : tariffRepository.findByCategory(category, pageable);

        return tariffs.map(this::toDto);
    }

    @Transactional(readOnly = true)
    public TariffDto getTariffById(UUID id, boolean isAdmin) {
        Tariff tariff = (isAdmin
                ? tariffRepository.findById(id)
                : tariffRepository.findByIdAndActiveTrue(id))
                .orElseThrow(() -> new TariffNotFoundException("tariff.not-found", id));
        return toDto(tariff);
    }

    @Transactional
    public TariffDto createTariff(CreateTariffDto dto) {
        String name = dto.name().trim();
        String description = dto.description() != null ? dto.description().trim() : null;
        BillingPeriod period = dto.billingPeriod() != null ? dto.billingPeriod() : BillingPeriod.MONTH;

        if (tariffRepository.insertIfAbsent(name, description, dto.category().name(), dto.price(), period.name()) == 0) {
            throw new TariffAlreadyExistsException("tariff.already-exists", name);
        }

        Tariff saved = tariffRepository.findByName(name)
                .orElseThrow(() -> new TariffNotFoundException("tariff.not-found-by-name", name));
        return toDto(saved);
    }

    @Transactional
    public TariffDto updateTariff(UUID id, UpdateTariffDto dto) {
        Tariff tariff = findTariffForUpdate(id);

        if (dto.name() != null) {
            String newName = dto.name().trim();
            if (tariffRepository.existsByNameAndIdNot(newName, id)) {
                throw new TariffAlreadyExistsException("tariff.already-exists", newName);
            }
            tariff.setName(newName);
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
        Tariff tariff = findTariffForUpdate(id);
        tariff.setActive(false);
        return toDto(tariff);
    }

    private Tariff findTariffForUpdate(UUID id) {
        return tariffRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new TariffNotFoundException("tariff.not-found", id));
    }

    private TariffDto toDto(Tariff tariff) {
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
package io.cvvexxx.services.tariff.controller;

import io.cvvexxx.services.tariff.dto.CreateTariffDto;
import io.cvvexxx.services.tariff.dto.TariffDto;
import io.cvvexxx.services.tariff.dto.UpdateTariffDto;
import io.cvvexxx.services.tariff.entity.TariffCategory;
import io.cvvexxx.services.tariff.service.TariffService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/services/tariffs")
public class TariffController {

    private final TariffService tariffService;

    @GetMapping
    public ResponseEntity<PagedModel<TariffDto>> getActiveTariffs(
            @RequestParam(name = "category", required = false) TariffCategory category,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(new PagedModel<>(tariffService.getActiveTariffs(category, pageable)));
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PagedModel<TariffDto>> getAllTariffs(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(new PagedModel<>(tariffService.getAllTariffs(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TariffDto> getTariff(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(tariffService.getTariffById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TariffDto> createTariff(@RequestBody @Valid CreateTariffDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tariffService.createTariff(dto));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TariffDto> updateTariff(
            @PathVariable("id") UUID id,
            @RequestBody @Valid UpdateTariffDto dto
    ) {
        return ResponseEntity.ok(tariffService.updateTariff(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TariffDto> archiveTariff(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(tariffService.archiveTariff(id));
    }
}
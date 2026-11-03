package io.cvvexxx.services.tariff.controller;

import io.cvvexxx.services.advice.InvalidSortException;
import io.cvvexxx.services.tariff.dto.CreateTariffDto;
import io.cvvexxx.services.tariff.dto.TariffDto;
import io.cvvexxx.services.tariff.dto.UpdateTariffDto;
import io.cvvexxx.services.tariff.entity.TariffCategory;
import io.cvvexxx.services.tariff.service.TariffService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/services/tariffs")
public class TariffController {

    private static final Set<String> SORT_FIELDS = Set.of("createdAt", "price", "name", "category");

    private final TariffService tariffService;

    @GetMapping
    public ResponseEntity<PagedModel<TariffDto>> getActiveTariffs(
            @RequestParam(name = "category", required = false) TariffCategory category,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        InvalidSortException.requireAllowed(pageable, SORT_FIELDS);
        return ResponseEntity.ok(new PagedModel<>(tariffService.getActiveTariffs(category, pageable)));
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PagedModel<TariffDto>> getAllTariffs(
            @RequestParam(name = "category", required = false) TariffCategory category,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        InvalidSortException.requireAllowed(pageable, SORT_FIELDS);
        return ResponseEntity.ok(new PagedModel<>(tariffService.getAllTariffs(category, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TariffDto> getTariff(
            @PathVariable("id") UUID id,
            Authentication authentication
    ) {
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return ResponseEntity.ok(tariffService.getTariffById(id, isAdmin));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TariffDto> createTariff(@RequestBody @Valid CreateTariffDto createTariffDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tariffService.createTariff(createTariffDto));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TariffDto> updateTariff(
            @PathVariable("id") UUID id,
            @RequestBody @Valid UpdateTariffDto updateTariffDto
    ) {
        return ResponseEntity.ok(tariffService.updateTariff(id, updateTariffDto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TariffDto> archiveTariff(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(tariffService.archiveTariff(id));
    }
}
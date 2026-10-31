package io.cvvexxx.services.tariff.repository;

import io.cvvexxx.services.tariff.entity.Tariff;
import io.cvvexxx.services.tariff.entity.TariffCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TariffRepository extends JpaRepository<Tariff, UUID> {

    Page<Tariff> findByActiveTrueOrderByCreatedAtDesc(Pageable pageable);

    Page<Tariff> findByActiveTrueAndCategoryOrderByCreatedAtDesc(TariffCategory category, Pageable pageable);

    Optional<Tariff> findByIdAndActiveTrue(UUID id);
}
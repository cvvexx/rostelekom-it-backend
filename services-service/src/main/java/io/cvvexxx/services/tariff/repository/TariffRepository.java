package io.cvvexxx.services.tariff.repository;

import io.cvvexxx.services.tariff.entity.Tariff;
import io.cvvexxx.services.tariff.entity.TariffCategory;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TariffRepository extends JpaRepository<Tariff, UUID> {

    Page<Tariff> findByActiveTrue(Pageable pageable);

    Page<Tariff> findByActiveTrueAndCategory(TariffCategory category, Pageable pageable);

    Page<Tariff> findByCategory(TariffCategory category, Pageable pageable);

    Optional<Tariff> findByIdAndActiveTrue(UUID id);

    Optional<Tariff> findByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tariff t where t.id = :id")
    Optional<Tariff> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Query(value = """
            insert into tariffs (id, name, description, category, price, currency, billing_period, active)
            values (gen_random_uuid(), :name, :description, :category, :price, 'RUB', :billingPeriod, true)
            on conflict (name, category) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("name") String name,
            @Param("description") String description,
            @Param("category") String category,
            @Param("price") BigDecimal price,
            @Param("billingPeriod") String billingPeriod
    );
}
package io.cvvexxx.billings.account.repository;

import io.cvvexxx.billings.account.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)//Для избежания lost update
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(UUID id);

    //Кастомный запрос чтобы не было race condition, если мы заходим в запрос, но другой поток уже создал аккаунт
    //то мы ниче не делаем и просто выходим из запроса
    @Modifying
    @Query(value = """
            insert into accounts (id, user_id, number)
            values (gen_random_uuid(), :userId, nextval('account_number_seq')::text)
            on conflict (user_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(UUID userId);

}

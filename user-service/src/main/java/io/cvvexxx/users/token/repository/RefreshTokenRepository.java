package io.cvvexxx.users.token.repository;

import io.cvvexxx.users.token.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository //Бин для работы с БД, реализацию интерфейса Spring Data генерирует сам
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    //Блокируем строку, чтобы не получилось так, что два запроса придут одновременно
    //и у нас будет два разных рефреш токена для одного юзера, чтобы недопустимо
    @Lock(LockModeType.PESSIMISTIC_WRITE) //SELECT ... FOR UPDATE (в SQLAlchemy .with_for_update())
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    //@Modifying - запрос меняет данные (update/delete), без нее Spring ждет select и упадет
    //@Query - свой запрос на JPQL: пишем по классам и полям (RefreshToken, t.user.id), а не по таблицам и колонкам
    //@Param("userId") - подставляет аргумент метода вместо :userId в запросе
    @Modifying
    @Query("""
            update RefreshToken t
               set t.revokedAt = :now
             where t.user.id = :userId
               and t.revokedAt is null
            """)
    int revokeAllByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :now")
    int deleteAllExpired(@Param("now") Instant now);
}

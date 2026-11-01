package io.cvvexxx.users.user.repository;

import io.cvvexxx.users.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

// Дефолтных методов в классе нет, т.к. JpaRepository сам имеет методы с которыми знает как работать без их описания в самом классе
// Поэтому дефолтные методы save, delete, update, findById и т.д. можно не описывать
@Repository // Аналогично @Service и @RestController создает бин
public interface UserRepository extends JpaRepository<User, UUID> {

    //Spring Data сам генерит запрос по имени метода: findByPhone -> select ... where phone = ?,
    //existsByPhone -> проверка есть ли запись с таким телефоном
    Optional<User> findByPhone(String phone);

    boolean existsByPhone(String phone);

    // Атомарное "создать, если нет": при гонке двух потоков с одним телефоном второй INSERT дождётся
    // коммита первого и просто ничего не вставит (вернёт 0) вместо ошибки unique-констрейнта.
    //@Modifying - запрос меняет данные, без нее Spring ждет select и упадет
    //nativeQuery = true - это обычный SQL под Postgres (как session.execute(text(...))), а не JPQL
    //@Param("id") - подставляет аргумент метода вместо :id в запросе
    @Modifying
    @Query(value = """
            insert into users (id, phone, last_name, first_name, middle_name, created_at, updated_at)
            values (:id, :phone, :lastName, :firstName, :middleName, :now, :now)
            on conflict do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("phone") String phone,
                       @Param("lastName") String lastName, @Param("firstName") String firstName,
                       @Param("middleName") String middleName, @Param("now") Instant now);

    @Modifying
    @Query(value = "insert into user_roles (user_id, role) values (:userId, :role)", nativeQuery = true)
    void insertRole(@Param("userId") UUID userId, @Param("role") String role);
}

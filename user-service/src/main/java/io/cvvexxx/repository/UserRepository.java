package io.cvvexxx.repository;

import io.cvvexxx.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

// Методов в классе нет, т.к. JpaRepository сам имеет методы с которыми знает как работать без их описания в самом классе
// Поэтому дефолтные методы save, delete, update, findById и т.д. можно не описывать
@Repository // Аналогично @Service и @RestController создает бин
public interface UserRepository extends JpaRepository<User, UUID> {
}

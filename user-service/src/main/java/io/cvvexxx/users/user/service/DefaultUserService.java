package io.cvvexxx.users.user.service;

import io.cvvexxx.users.user.dto.UserProfileDto;
import io.cvvexxx.users.user.entity.Role;
import io.cvvexxx.users.user.entity.User;
import io.cvvexxx.users.user.exception.UserNotFoundException;
import io.cvvexxx.users.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service //Аннотация спринга, аналогично @RestController, только эта просто создает бин класса
@RequiredArgsConstructor
//Lombok-аннотация, которая неявно создает конструктор, включающий в себя все final-поля, через него Spring внедряет зависимости (как Depends() в FastAPI)
@Slf4j //Lombok-аннотация, которая неявно создает логгер (log) в этом классе (как log = logging.getLogger(__name__))
public class DefaultUserService implements UserService {

    // Внедряем Repository, чтобы делегировать ему работу с базой данных
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean existsByPhone(String phone) {
        return userRepository.existsByPhone(phone);
    }

    //ФИО используется только при создании, у существующего юзера не перезаписывается
    @Override
    @Transactional //Весь метод в одной транзакции (как async with session.begin()), при RuntimeException будет rollback
    public User findOrCreateByPhone(String phone, String lastName, String firstName, String middleName) {
        return userRepository.findByPhone(phone)//Ищем юзера в бд, если находим, то возвращаем
                .orElseGet(() -> createUser(phone, lastName, firstName, middleName));//Иначе создаем его
    }

    @Override
    @Transactional(readOnly = true) //Транзакция только на чтение, Hibernate не отслеживает изменения - чуть быстрее
    public UserProfileDto getProfile(UUID userId) {
        return toDto(requireUser(userId));//мапим в дто полученного из бд юзера
    }

    @Override
    @Transactional
    public void grantAdminRole(UUID userId) {
        User user = requireUser(userId);

        if (user.getRoles().add(Role.ADMIN)) {
            log.warn("Пользователь с id {} получил роль ADMIN", userId);
        }
    }

    private User createUser(String phone, String lastName, String firstName, String middleName) {
        //Если два потока одновременно захотят создать юзера, то это здесь обрабатывается
        UUID id = UUID.randomUUID();
        //Атомарная операция, возвращает 1, если юзера создает, и 0, если конфликт и такой юзер уже есть
        if (userRepository.insertIfAbsent(id, phone, trimToNull(lastName), trimToNull(firstName),
                trimToNull(middleName), Instant.now()) == 1) {
            userRepository.insertRole(id, Role.USER.name());//Если юзер создан, то привязываем ему роль
            log.info("Created user {} on first successful OTP verification", id);
        } else {
            log.debug("Concurrent user creation for the same phone, reading the existing one");
        }
        //В любой случае читаем и возвращаем юзера
        return userRepository.findByPhone(phone)
                .orElseThrow(() -> new IllegalStateException("User with phone was not created"));
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    //Ищем юзера по id, иначе выбрасываем exception
    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("user.not-found", userId));
    }

    private UserProfileDto toDto(User user) {
        return new UserProfileDto(
                user.getId(),
                user.getPhone(),
                user.getFirstName(),
                user.getLastName(),
                user.getMiddleName()
        );
    }
}

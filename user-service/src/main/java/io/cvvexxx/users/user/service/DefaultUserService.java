package io.cvvexxx.users.user.service;

import io.cvvexxx.users.user.dto.UpdateProfileDto;
import io.cvvexxx.users.user.dto.UserProfileDto;
import io.cvvexxx.users.user.entity.Role;
import io.cvvexxx.users.user.entity.User;
import io.cvvexxx.users.user.exception.FieldAlreadyExistsException;
import io.cvvexxx.users.user.exception.UserNotFoundException;
import io.cvvexxx.users.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service //Аннотация спринга, аналогично @RestController, только эта просто создает бин класса
@RequiredArgsConstructor //Lombok-аннотация, которая неявно создает конструктор, включающий в себя все final-поля, через него Spring внедряет зависимости (как Depends() в FastAPI)
@Slf4j //Lombok-аннотация, которая неявно создает логгер (log) в этом классе (как log = logging.getLogger(__name__))
public class DefaultUserService implements UserService {

    // Внедряем Repository, чтобы делегировать ему работу с базой данных
    private final UserRepository userRepository;

    @Override
    @Transactional //Весь метод в одной транзакции (как async with session.begin()), при RuntimeException будет rollback
    public User findOrCreateByPhone(String phone) {
        return userRepository.findByPhone(phone)//Ищем юзера в бд, если находим, то возвращаем
                .orElseGet(() -> createUser(phone));//Иначе создаем его
    }

    @Override
    @Transactional(readOnly = true) //Транзакция только на чтение, Hibernate не отслеживает изменения - чуть быстрее
    public UserProfileDto getProfile(UUID userId) {
        return toDto(requireUser(userId));//мапим в дто полученного из бд юзера
    }

    //Метод обновления профиля юзера
    @Override
    @Transactional
    public UserProfileDto updateProfile(UUID userId, UpdateProfileDto dto) {
        User user = requireUser(userId);

        if (dto.username() != null) {
            String username = dto.username().trim().toLowerCase(Locale.ROOT);//Приводим username к нормальному виду
            if (!username.equals(user.getUsername()) && userRepository.existsByUsername(username)) {
                throw new FieldAlreadyExistsException("username", username,
                        "user.username-exists", username);
            }
            user.setUsername(username);
        }
        if (dto.firstName() != null) {
            user.setFirstName(dto.firstName().trim());
        }
        if (dto.lastName() != null) {
            user.setLastName(dto.lastName().trim());
        }

        try {
            return toDto(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) {
            throw new FieldAlreadyExistsException("username", user.getUsername(),
                    "user.username-exists", user.getUsername());
        }
    }

    private User createUser(String phone) {
        //Если два потока одновременно захотят создать юзера, то это здесь обрабатывается
        UUID id = UUID.randomUUID();
        //Атомарная операция, возвращает 1, если юзера создает, и 0, если конфликт и такой юзер уже есть
        if (userRepository.insertIfAbsent(id, phone, Instant.now()) == 1) {
            userRepository.insertRole(id, Role.USER.name());//Если юзер создан, то привязываем ему роль
            log.info("Created user {} on first successful OTP verification", id);
        } else {
            log.debug("Concurrent user creation for the same phone, reading the existing one");
        }
        //В любой случае читаем и возвращаем юзера
        return userRepository.findByPhone(phone)
                .orElseThrow(() -> new IllegalStateException("User with phone was not created"));
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
                user.getUsername(),
                user.getFirstName(),
                user.getLastName()
        );
    }
}

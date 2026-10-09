package io.cvvexxx.users.service;


import io.cvvexxx.users.dto.CreatedUserDto;
import io.cvvexxx.users.dto.NewUserDto;
import io.cvvexxx.users.entity.User;
import io.cvvexxx.users.exception.FieldAlreadyExistsException;
import io.cvvexxx.users.repository.UserRepository;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Locale;
import java.util.UUID;

@Service //Аннотация спринга, аналогично @RestController, только эта просто создает бин класса
@RequiredArgsConstructor //Lombok-аннотация, которая неявно создает конструктор, включающий в себя все final-поля
@Slf4j //Lombok-аннотация, которая неявно создает логгер (log) в этом классе
public class DefaultUserService implements UserService {

    private final Keycloak keycloak;
    // Внедряем Repository, чтобы делегировать ему работу с базой данных
    private final UserRepository userRepository;

    //@Value отвечает за внедрение данных из application.properties, достаем значение переменной keycloak.realm
    @Value("${keycloak.realm}")
    private String realm; // realm(пространство для Keycloak)


    // в этом методе просто бизнес логика, подготавливаем данные для отправки в Keycloak для регистрации
    // и сохраняем в свою users_db. Построчно впадлу писать, мб потом для Олега напишу
    @Override
    public CreatedUserDto registerUser(NewUserDto dto) {
        String username = dto.username().trim().toLowerCase(Locale.ROOT);
        String firstName = dto.firstName().trim();
        String lastName = dto.lastName().trim();

        UserRepresentation user = getUserRepresentation(username, firstName, lastName, dto.password());
        UsersResource usersResource = keycloak.realm(realm).users();

        String keycloakUserId;
        try (Response response = usersResource.create(user)) {
            int status = response.getStatus();

            if (status == 409) {
                throw new FieldAlreadyExistsException("username", username,
                        "User with username '%s' already exists".formatted(username));
            }

            if (status != 201) {
                throw new IllegalStateException("Failed to create user in Keycloak. Status: " + status);
            }

            String location = response.getHeaderString("Location");
            if (location == null || location.isBlank()) {
                throw new IllegalStateException("Keycloak response did not include a Location header");
            }
            keycloakUserId = location.substring(location.lastIndexOf('/') + 1);
        }

        try {
            //сохраняем в бд сразу после вызова этого метода
            User saved = userRepository.saveAndFlush(
                    new User(UUID.fromString(keycloakUserId), username, firstName, lastName)
            );

            //Если все норм, то возвращаем, если нет, то проваливаемся в catch
            return new CreatedUserDto(saved.getId(), saved.getUsername());
        } catch (RuntimeException e) {
            log.error("Failed to save user. Rolling back Keycloak user {}", keycloakUserId, e);
            try {
                //т.к. не получилось сохранить юзера в бд, то соответственно удаляем его из Keycloak
                usersResource.get(keycloakUserId).remove();
            } catch (Exception ex) {
                log.error("Failed to rollback Keycloak user deletion for ID: {}", keycloakUserId, ex);
            }
            throw e;
        }
    }

    private UserRepresentation getUserRepresentation(
            String username, String firstName, String lastName, String password
    ) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false);

        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEnabled(true);
        user.setCredentials(Collections.singletonList(credential));
        return user;
    }

}

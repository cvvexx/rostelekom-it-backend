package io.cvvexxx.service;


import io.cvvexxx.dto.CreatedUserDto;
import io.cvvexxx.dto.NewUserDto;
import io.cvvexxx.entity.User;
import io.cvvexxx.exception.FieldAlreadyExistsException;
import io.cvvexxx.repository.UserRepository;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultUserService implements UserService {

    private final Keycloak keycloak;
    private final UserRepository userRepository;

    @Value("${keycloak.realm}")
    private String realm;

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
            User saved = userRepository.saveAndFlush(
                    new User(UUID.fromString(keycloakUserId), username, firstName, lastName)
            );

            return new CreatedUserDto(saved.getId(), saved.getUsername());
        } catch (RuntimeException e) {
            log.error("Failed to save user. Rolling back Keycloak user {}", keycloakUserId, e);
            try {
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

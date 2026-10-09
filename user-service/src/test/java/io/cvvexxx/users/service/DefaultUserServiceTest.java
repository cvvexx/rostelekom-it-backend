package io.cvvexxx.users.service;

import io.cvvexxx.users.dto.CreatedUserDto;
import io.cvvexxx.users.dto.NewUserDto;
import io.cvvexxx.users.entity.User;
import io.cvvexxx.users.exception.FieldAlreadyExistsException;
import io.cvvexxx.users.repository.UserRepository;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class DefaultUserServiceTest {

    private static final String REALM = "test-realm";
    private static final String LOCATION_PREFIX = "http://localhost:8080/admin/realms/test-realm/users/";

    @Mock
    private Keycloak keycloak;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RealmResource realmResource;
    @Mock
    private UsersResource usersResource;
    @Mock
    private UserResource userResource;
    @Mock
    private Response response;

    @InjectMocks
    private DefaultUserService userService;

    @BeforeEach
    void setUp() {
        // @Value не работает без Spring-контекста, поэтому проставляем поле вручную
        ReflectionTestUtils.setField(userService, "realm", REALM);

        when(keycloak.realm(REALM)).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
    }

    private NewUserDto dto() {
        return new NewUserDto("  JohnDoe  ", "secret123", "  John ", " Doe  ");
    }

    private void mockCreatedResponse(UUID keycloakId) {
        when(response.getStatus()).thenReturn(201);
        when(response.getHeaderString("Location")).thenReturn(LOCATION_PREFIX + keycloakId);
    }

    @Test
    @DisplayName("registerUser: успешная регистрация возвращает DTO с id из Keycloak и нормализованным username")
    void registerUser_success() {
        UUID keycloakId = UUID.randomUUID();
        mockCreatedResponse(keycloakId);
        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CreatedUserDto result = userService.registerUser(dto());

        assertThat(result.id()).isEqualTo(keycloakId);
        assertThat(result.username()).isEqualTo("johndoe");
        verify(response).close();
    }

    @Test
    @DisplayName("registerUser: в Keycloak уходит UserRepresentation с обрезанными и нормализованными данными")
    void registerUser_sendsCorrectRepresentationToKeycloak() {
        mockCreatedResponse(UUID.randomUUID());
        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        userService.registerUser(dto());

        ArgumentCaptor<UserRepresentation> captor = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(usersResource).create(captor.capture());
        UserRepresentation sent = captor.getValue();

        assertThat(sent.getUsername()).isEqualTo("johndoe");
        assertThat(sent.getFirstName()).isEqualTo("John");
        assertThat(sent.getLastName()).isEqualTo("Doe");
        assertThat(sent.isEnabled()).isTrue();
        assertThat(sent.getCredentials()).hasSize(1);

        CredentialRepresentation credential = sent.getCredentials().get(0);
        assertThat(credential.getType()).isEqualTo(CredentialRepresentation.PASSWORD);
        assertThat(credential.getValue()).isEqualTo("secret123");
        assertThat(credential.isTemporary()).isFalse();
    }

    @Test
    @DisplayName("registerUser: в БД сохраняется пользователь с id из Keycloak и нормализованными полями")
    void registerUser_savesCorrectEntity() {
        UUID keycloakId = UUID.randomUUID();
        mockCreatedResponse(keycloakId);
        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        userService.registerUser(dto());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getId()).isEqualTo(keycloakId);
        assertThat(saved.getUsername()).isEqualTo("johndoe");
        assertThat(saved.getFirstName()).isEqualTo("John");
        assertThat(saved.getLastName()).isEqualTo("Doe");
    }

    @Test
    @DisplayName("registerUser: Keycloak вернул 409 — FieldAlreadyExistsException, в БД ничего не пишется")
    void registerUser_conflict_throwsFieldAlreadyExists() {
        when(response.getStatus()).thenReturn(409);

        assertThatThrownBy(() -> userService.registerUser(dto()))
                .isInstanceOf(FieldAlreadyExistsException.class)
                .hasMessage("User with username 'johndoe' already exists");

        verify(userRepository, never()).saveAndFlush(any());
        verify(response).close();
    }

    @Test
    @DisplayName("registerUser: Keycloak вернул неожиданный статус — IllegalStateException")
    void registerUser_unexpectedStatus_throwsIllegalState() {
        when(response.getStatus()).thenReturn(500);

        assertThatThrownBy(() -> userService.registerUser(dto()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to create user in Keycloak")
                .hasMessageContaining("500");

        verify(userRepository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("registerUser: нет заголовка Location — IllegalStateException")
    void registerUser_missingLocation_throwsIllegalState(String location) {
        when(response.getStatus()).thenReturn(201);
        when(response.getHeaderString("Location")).thenReturn(location);

        assertThatThrownBy(() -> userService.registerUser(dto()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Keycloak response did not include a Location header");

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("registerUser: сбой сохранения в БД — пользователь удаляется из Keycloak, исключение пробрасывается")
    void registerUser_dbFailure_rollsBackKeycloakUser() {
        UUID keycloakId = UUID.randomUUID();
        mockCreatedResponse(keycloakId);
        RuntimeException dbError = new RuntimeException("DB is down");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(dbError);
        when(usersResource.get(keycloakId.toString())).thenReturn(userResource);

        assertThatThrownBy(() -> userService.registerUser(dto()))
                .isSameAs(dbError);

        verify(usersResource).get(keycloakId.toString());
        verify(userResource).remove();
    }

    @Test
    @DisplayName("registerUser: сбой БД и сбой отката — наружу летит исходная ошибка БД, а не ошибка отката")
    void registerUser_dbFailureAndRollbackFailure_throwsOriginalException() {
        UUID keycloakId = UUID.randomUUID();
        mockCreatedResponse(keycloakId);
        RuntimeException dbError = new RuntimeException("DB is down");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(dbError);
        when(usersResource.get(keycloakId.toString())).thenReturn(userResource);
        doThrow(new RuntimeException("Keycloak is down too")).when(userResource).remove();

        assertThatThrownBy(() -> userService.registerUser(dto()))
                .isSameAs(dbError);

        verify(userResource).remove();
    }
}
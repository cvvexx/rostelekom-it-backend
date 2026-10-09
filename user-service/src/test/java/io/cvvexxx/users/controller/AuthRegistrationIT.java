package io.cvvexxx.users.controller;

import io.cvvexxx.users.entity.User;
import io.cvvexxx.users.repository.UserRepository;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthRegistrationIT {

    private static final String REALM = "test-realm";
    private static final String LOCATION_PREFIX =
            "http://localhost:8080/admin/realms/test-realm/users/";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private Keycloak keycloak;

    private UsersResource usersResource;
    private UserResource userResource;
    private Response keycloakResponse;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        RealmResource realmResource = mock(RealmResource.class);
        usersResource = mock(UsersResource.class);
        userResource = mock(UserResource.class);
        keycloakResponse = mock(Response.class);

        when(keycloak.realm(REALM)).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(keycloakResponse);
    }

    private void keycloakCreates(UUID id) {
        when(keycloakResponse.getStatus()).thenReturn(201);
        when(keycloakResponse.getHeaderString("Location")).thenReturn(LOCATION_PREFIX + id);
    }

    private static String body(String username, String firstName, String lastName, String password) {
        return """
                {"username": "%s", "firstName": "%s", "lastName": "%s", "password": "%s"}
                """.formatted(username, firstName, lastName, password);
    }

    @Test
    @DisplayName("POST /api/users/register: пользователь создаётся в Keycloak и сохраняется в БД")
    void register_success() throws Exception {
        UUID keycloakId = UUID.randomUUID();
        keycloakCreates(keycloakId);

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("  JohnDoe ", " John ", " Doe ", "secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(keycloakId.toString()))
                .andExpect(jsonPath("$.username").value("johndoe"));

        User saved = userRepository.findById(keycloakId).orElseThrow();
        assertThat(saved.getUsername()).isEqualTo("johndoe");
        assertThat(saved.getFirstName()).isEqualTo("John");
        assertThat(saved.getLastName()).isEqualTo("Doe");

        verify(usersResource).create(any(UserRepresentation.class));
        verify(usersResource, never()).get(any()); // откат не вызывался
    }

    @Test
    @DisplayName("POST /api/users/register: невалидное тело — 400, ни Keycloak, ни БД не затронуты")
    void register_invalidBody_returns400() throws Exception {
        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("", "John", "Doe", "secret123")))
                .andExpect(status().isBadRequest());

        verify(usersResource, never()).create(any(UserRepresentation.class));
        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("POST /api/users/register: Keycloak вернул 409 — ответ 409, в БД пусто")
    void register_keycloakConflict_returns409() throws Exception {
        when(keycloakResponse.getStatus()).thenReturn(409);

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("johndoe", "John", "Doe", "secret123")))
                .andExpect(status().isConflict());

        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("POST /api/users/register: сбой записи в БД — пользователь удаляется из Keycloak")
    void register_dbFailure_rollsBackKeycloakUser() throws Exception {
        // В БД уже есть пользователь с таким username, поэтому saveAndFlush
        // в сервисе упадёт на unique-ограничении
        userRepository.saveAndFlush(new User(UUID.randomUUID(), "johndoe", "Old", "User"));

        UUID keycloakId = UUID.randomUUID();
        keycloakCreates(keycloakId);
        when(usersResource.get(keycloakId.toString())).thenReturn(userResource);

        // Если есть общий @ExceptionHandler(Exception.class), замените на
        // perform(...).andExpect(status().isInternalServerError())
        assertThatThrownBy(() -> mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("johndoe", "John", "Doe", "secret123"))))
                .hasCauseInstanceOf(DataIntegrityViolationException.class);

        verify(userResource).remove();
        assertThat(userRepository.count()).isEqualTo(1); // остался только старый пользователь
    }
}
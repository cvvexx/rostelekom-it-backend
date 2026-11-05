package io.cvvexxx.users.user.controller;

import io.cvvexxx.users.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

//Назначения пользователя админом, если только в профиле dev
@Profile("dev")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class DevAdminController {

    private final UserService userService;

    @PostMapping("/admin") //POST /api/users/admin
    public ResponseEntity<Void> grantAdminRole(@AuthenticationPrincipal Jwt jwt) {
        userService.grantAdminRole(UUID.fromString(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }
}

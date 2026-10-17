package io.cvvexxx.users.user.controller;

import io.cvvexxx.users.user.dto.UpdateProfileDto;
import io.cvvexxx.users.user.dto.UserProfileDto;
import io.cvvexxx.users.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserProfileDto> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(userService.getProfile(currentUserId(jwt)));
    }

    @PatchMapping("/me")
    public ResponseEntity<UserProfileDto> updateMe(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid UpdateProfileDto dto
    ) {
        return ResponseEntity.ok(userService.updateProfile(currentUserId(jwt), dto));
    }

    private UUID currentUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}

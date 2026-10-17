package io.cvvexxx.users.user.dto;

import java.util.UUID;

public record UserProfileDto(
        UUID id,
        String phone,
        String username,
        String firstName,
        String lastName
) {
}

package io.cvvexxx.users.user.dto;

import jakarta.validation.constraints.Size;

//DTO для обновления информации о юзере
public record UpdateProfileDto(
        //@Size пропускает null, т.е. поле можно не передавать (как Field(None, min_length=3, max_length=20))
        @Size(min = 3, max = 20, message = "{validation.profile-update.username.size}")
        String username,

        @Size(max = 100, message = "{validation.profile-update.first-name.too-long}")
        String firstName,

        @Size(max = 100, message = "{validation.profile-update.last-name.too-long}")
        String lastName
) {
}

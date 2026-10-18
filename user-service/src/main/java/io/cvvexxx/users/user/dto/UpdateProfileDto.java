package io.cvvexxx.users.user.dto;

import jakarta.validation.constraints.Size;

//DTO для обновления информации о юзере
public record UpdateProfileDto(
        @Size(min = 3, max = 20, message = "Username должен быть от 3 до 20 символов")
        String username,

        @Size(max = 100, message = "Имя слишком длинное")
        String firstName,

        @Size(max = 100, message = "Фамилия слишком длинная")
        String lastName
) {
}

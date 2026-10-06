package io.cvvexxx.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

//TODO(добавить сообщения об ошибках)
public record NewUserDto(
        @NotBlank
        @Size(min = 3, max = 20)
        String username,

        @Size(min = 8, max = 32)
        String password,

        @NotBlank
        @Size(max = 100)
        String firstName,

        @NotBlank
        @Size(max = 100)
        String lastName
) {
}

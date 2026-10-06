package io.cvvexxx.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

//TODO(добавить сообщения об ошибках)
//DTO(Data Transfer Object) неизменяемые записи(record) для API, чаще всего используются, чтобы принять/отдать объект наружу
public record NewUserDto(
        //Ниже - аннотации, которые позволяют валидировать входные данные не в самом контроллере, а еще до захода в него
        @NotBlank //Не пустая строка
        @Size(min = 3, max = 20) //Длина строки
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

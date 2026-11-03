package io.cvvexxx.users.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

//DTO для регистрации нового юзера, приходит ПОСЛЕ /otp/verify, который вернул 202 и registrationToken
//Фамилия и имя обязательны, отчество по желанию (у части людей его нет)
public record RegisterDto(
        @NotBlank(message = "{validation.register.registration-token.required}") //Не null, не пустая и не из одних пробелов
        @Size(max = 64, message = "{validation.register.registration-token.too-long}") //Токен 43 символа, длиннее - точно мусор
        String registrationToken,

        @NotBlank(message = "{validation.full-name.last-name.required}")
        @Size(max = 100, message = "{validation.full-name.last-name.too-long}") //Как Field(max_length=100)
        @Pattern(regexp = NAME_PATTERN, message = "{validation.full-name.last-name.pattern}") //Как Field(pattern=...)
        String lastName,

        @NotBlank(message = "{validation.full-name.first-name.required}")
        @Size(max = 100, message = "{validation.full-name.first-name.too-long}")
        @Pattern(regexp = NAME_PATTERN, message = "{validation.full-name.first-name.pattern}")
        String firstName,

        @Size(max = 100, message = "{validation.full-name.middle-name.too-long}")
        @Pattern(regexp = NAME_PATTERN, message = "{validation.full-name.middle-name.pattern}")
        String middleName
) {
    //Буквы любого алфавита, между ними пробел, дефис или апостроф
    //Пустая строка проходит паттерн: у фамилии и имени ее отсекает @NotBlank, пустое отчество станет null
    private static final String NAME_PATTERN = "\\s*(\\p{L}+([ '’-]\\p{L}+)*)?\\s*";
}

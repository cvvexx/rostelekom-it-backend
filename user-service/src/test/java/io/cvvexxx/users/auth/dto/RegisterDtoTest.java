package io.cvvexxx.users.auth.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterDtoTest {

    private static final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static final String TOKEN = "registration-token";

    @ParameterizedTest
    @ValueSource(strings = {"Иван", "Пётр", "John", "Анна-Мария", "Д'Артаньян", "Д’Артаньян", "Сен Жермен", " Иван "})
    @DisplayName("ФИО: буквы любого алфавита, одиночные пробел/дефис/апостроф между ними — валидны")
    void validName_passes(String name) {
        assertThat(validate(TOKEN, name, name, name)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "Иван1", "!!!", "<script>", "-Иван", "Иван-", "Иван--Петров", "Анна  Мария", "'"})
    @DisplayName("ФИО: цифры, спецсимволы и разделители не между буквами — ошибка на каждом поле")
    void invalidName_rejectedForEveryField(String name) {
        assertThat(validate(TOKEN, name, name, name))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("lastName", "firstName", "middleName");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("Фамилия и имя обязательны, отчество можно не указывать")
    void blankName_requiredOnlyForLastAndFirstName(String name) {
        assertThat(validate(TOKEN, name, name, name))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("lastName", "firstName");
    }

    @Test
    @DisplayName("Без токена регистрации DTO не проходит валидацию")
    void blankToken_rejected() {
        assertThat(validate(" ", "Иванов", "Иван", null))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("registrationToken");
    }

    private static Set<ConstraintViolation<RegisterDto>> validate(String token, String lastName,
                                                                 String firstName, String middleName) {
        return validator.validate(new RegisterDto(token, lastName, firstName, middleName));
    }
}

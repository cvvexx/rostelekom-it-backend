package io.cvvexxx.users.auth.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OtpVerifyDtoTest {

    private static final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Иван", "Пётр", "John", "Анна-Мария", "Д'Артаньян", "Д’Артаньян", "Сен Жермен", " Иван "})
    @DisplayName("ФИО: буквы любого алфавита, одиночные пробел/дефис/апостроф между ними, пустое значение — валидны")
    void validName_passes(String name) {
        assertThat(validate(name)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "Иван1", "!!!", "<script>", "-Иван", "Иван-", "Иван--Петров", "Анна  Мария", "'"})
    @DisplayName("ФИО: цифры, спецсимволы и разделители не между буквами — ошибка на каждом поле")
    void invalidName_rejectedForEveryField(String name) {
        assertThat(validate(name))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("lastName", "firstName", "middleName");
    }

    private static Set<ConstraintViolation<OtpVerifyDto>> validate(String name) {
        return validator.validate(new OtpVerifyDto("+79991234567", "1234", name, name, name));
    }
}

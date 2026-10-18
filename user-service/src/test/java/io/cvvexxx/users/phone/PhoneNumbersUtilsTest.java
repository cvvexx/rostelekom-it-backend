package io.cvvexxx.users.phone;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneNumbersUtilsTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "+79991234567",
            "89991234567",
            "79991234567",
            "9991234567",
            "+7 (999) 123-45-67",
            "8 999 123 45 67"
    })
    @DisplayName("normalize: любой формат российского номера приводится к +79991234567")
    void normalize_variousFormats_produceSameCanonicalNumber(String raw) {
        assertThat(PhoneNumbersUtils.normalize(raw)).isEqualTo("+79991234567");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "123", "+1234567890123456", "abc"})
    @DisplayName("normalize: некорректный номер — InvalidPhoneNumberException")
    void normalize_invalidInput_throws(String raw) {
        assertThatThrownBy(() -> PhoneNumbersUtils.normalize(raw))
                .isInstanceOf(InvalidPhoneNumberException.class);
    }

    @Test
    @DisplayName("mask: в логи уходит только начало и конец номера")
    void mask_hidesMiddleOfNumber() {
        assertThat(PhoneNumbersUtils.mask("+79991234567")).isEqualTo("+7999***4567");
    }
}

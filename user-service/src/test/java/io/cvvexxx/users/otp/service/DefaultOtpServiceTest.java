package io.cvvexxx.users.otp.service;

import io.cvvexxx.users.otp.config.OtpProperties;
import io.cvvexxx.users.otp.dto.OtpChallengeDto;
import io.cvvexxx.users.otp.exception.InvalidOtpException;
import io.cvvexxx.users.otp.exception.OtpRequestNotAllowedException;
import io.cvvexxx.users.otp.store.InMemoryOtpStore;
import io.cvvexxx.users.sms.SmsSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DefaultOtpServiceTest {

    private static final String PHONE = "+79991234567";

    private InMemoryOtpStore otpStore;
    private SmsSender smsSender;
    private DefaultOtpService otpService;

    private OtpProperties properties(boolean exposeCode, Duration cooldown, int maxSends, int maxAttempts) {
        return new OtpProperties(
                6,
                Duration.ofMinutes(5),
                cooldown,
                Duration.ofHours(1),
                maxSends,
                maxAttempts,
                exposeCode,
                "memory"
        );
    }

    @BeforeEach
    void setUp() {
        otpStore = new InMemoryOtpStore();
        smsSender = mock(SmsSender.class);
        otpService = new DefaultOtpService(otpStore, smsSender, properties(true, Duration.ofSeconds(60), 5, 3));
    }

    @Test
    @DisplayName("requestCode: код генерируется, уходит в SmsSender и сохраняется в хранилище")
    void requestCode_generatesStoresAndSendsCode() {
        OtpChallengeDto challenge = otpService.requestCode(PHONE);

        assertThat(challenge.phone()).isEqualTo(PHONE);
        assertThat(challenge.code()).isNotNull().hasSize(6).containsOnlyDigits();
        assertThat(challenge.expiresInSeconds()).isEqualTo(300);
        assertThat(challenge.resendAfterSeconds()).isEqualTo(60);

        assertThat(otpStore.peekCode(PHONE)).contains(challenge.code());
        verify(smsSender).send(eq(PHONE), contains(challenge.code()));
    }

    @Test
    @DisplayName("requestCode: при expose-code=false код не возвращается наружу, но SMS всё равно уходит")
    void requestCode_withExposeCodeDisabled_hidesCodeFromResponse() {
        otpService = new DefaultOtpService(otpStore, smsSender, properties(false, Duration.ofSeconds(60), 5, 3));

        OtpChallengeDto challenge = otpService.requestCode(PHONE);

        assertThat(challenge.code()).isNull();
        assertThat(otpStore.peekCode(PHONE)).isPresent();
        verify(smsSender).send(eq(PHONE), anyString());
    }

    @Test
    @DisplayName("requestCode: повторный запрос до истечения кулдауна — 429 с временем ожидания")
    void requestCode_withinCooldown_throws() {
        otpService.requestCode(PHONE);

        assertThatThrownBy(() -> otpService.requestCode(PHONE))
                .isInstanceOf(OtpRequestNotAllowedException.class)
                .extracting(e -> ((OtpRequestNotAllowedException) e).getRetryAfter())
                .satisfies(retryAfter -> assertThat((Duration) retryAfter).isPositive());
    }

    @Test
    @DisplayName("requestCode: превышен лимит отправок за окно — запрос отклоняется")
    void requestCode_exceedingWindowLimit_throws() {
        otpService = new DefaultOtpService(otpStore, smsSender, properties(true, Duration.ZERO, 2, 3));

        otpService.requestCode(PHONE);
        otpService.requestCode(PHONE);

        assertThatThrownBy(() -> otpService.requestCode(PHONE))
                .isInstanceOf(OtpRequestNotAllowedException.class)
                .hasMessage("otp.too-many-requests")
                .extracting(e -> ((OtpRequestNotAllowedException) e).getRetryAfter())
                .satisfies(retryAfter -> assertThat((Duration) retryAfter)
                        .isPositive()
                        .isLessThanOrEqualTo(Duration.ofHours(1)));
    }

    @Test
    @DisplayName("verifyCode: верный код проходит и сразу удаляется — повторно его использовать нельзя")
    void verifyCode_correctCode_passesAndBurnsCode() {
        String code = otpService.requestCode(PHONE).code();

        otpService.verifyCode(PHONE, code);

        assertThat(otpStore.peekCode(PHONE)).isEmpty();
        assertThatThrownBy(() -> otpService.verifyCode(PHONE, code))
                .isInstanceOf(InvalidOtpException.class)
                .extracting(e -> ((InvalidOtpException) e).getReason())
                .isEqualTo(InvalidOtpException.Reason.NOT_REQUESTED);
    }

    @Test
    @DisplayName("verifyCode: код не запрашивали — NOT_REQUESTED")
    void verifyCode_withoutRequest_throwsNotRequested() {
        assertThatThrownBy(() -> otpService.verifyCode(PHONE, "123456"))
                .isInstanceOf(InvalidOtpException.class)
                .extracting(e -> ((InvalidOtpException) e).getReason())
                .isEqualTo(InvalidOtpException.Reason.NOT_REQUESTED);
    }

    @Test
    @DisplayName("verifyCode: неверный код уменьшает счётчик оставшихся попыток")
    void verifyCode_wrongCode_decreasesAttemptsLeft() {
        String code = otpService.requestCode(PHONE).code();
        String wrong = wrongCodeFor(code);

        assertThatThrownBy(() -> otpService.verifyCode(PHONE, wrong))
                .isInstanceOf(InvalidOtpException.class)
                .extracting(e -> ((InvalidOtpException) e).getAttemptsLeft())
                .isEqualTo(2);

        assertThatThrownBy(() -> otpService.verifyCode(PHONE, wrong))
                .extracting(e -> ((InvalidOtpException) e).getAttemptsLeft())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("verifyCode: попытки исчерпаны — код сжигается, даже правильный больше не подойдёт")
    void verifyCode_attemptsExceeded_burnsCode() {
        String code = otpService.requestCode(PHONE).code();
        String wrong = wrongCodeFor(code);

        assertThatThrownBy(() -> otpService.verifyCode(PHONE, wrong)).isInstanceOf(InvalidOtpException.class);
        assertThatThrownBy(() -> otpService.verifyCode(PHONE, wrong)).isInstanceOf(InvalidOtpException.class);
        assertThatThrownBy(() -> otpService.verifyCode(PHONE, wrong))
                .extracting(e -> ((InvalidOtpException) e).getReason())
                .isEqualTo(InvalidOtpException.Reason.ATTEMPTS_EXCEEDED);

        assertThat(otpStore.peekCode(PHONE)).isEmpty();
        assertThatThrownBy(() -> otpService.verifyCode(PHONE, code))
                .extracting(e -> ((InvalidOtpException) e).getReason())
                .isEqualTo(InvalidOtpException.Reason.NOT_REQUESTED);
    }

    @Test
    @DisplayName("verifyCode: протухший код не принимается")
    void verifyCode_expiredCode_isRejected() {
        otpStore.save(PHONE, "123456", Duration.ofMillis(-1));

        assertThatThrownBy(() -> otpService.verifyCode(PHONE, "123456"))
                .isInstanceOf(InvalidOtpException.class)
                .extracting(e -> ((InvalidOtpException) e).getReason())
                .isEqualTo(InvalidOtpException.Reason.NOT_REQUESTED);

        verify(smsSender, never()).send(anyString(), anyString());
    }

    private static String wrongCodeFor(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }
}

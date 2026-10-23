package io.cvvexxx.users.otp.service;

import io.cvvexxx.users.otp.config.OtpProperties;
import io.cvvexxx.users.otp.dto.OtpChallengeDto;
import io.cvvexxx.users.otp.exception.InvalidOtpException;
import io.cvvexxx.users.otp.exception.OtpRequestNotAllowedException;
import io.cvvexxx.users.otp.store.OtpStore;
import io.cvvexxx.users.otp.store.OtpVerification;
import io.cvvexxx.users.phone.PhoneNumbersUtils;
import io.cvvexxx.users.sms.SmsSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

@Service //Бин, Spring создаст один объект и подставит его в DefaultAuthService
@RequiredArgsConstructor //Через конструктор приходят OtpStore, SmsSender, OtpProperties (те, что из OtpBeans)
@Slf4j //Создает поле log
public class DefaultOtpService implements OtpService {

    private static final String SMS_TEMPLATE = "Код для входа: %s. Никому его не сообщайте.";

    private final OtpStore otpStore;
    private final SmsSender smsSender;
    private final OtpProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public OtpChallengeDto requestCode(String normalizedPhone) {
        //Проверяем, прошло ли 60 секунд с предыдущего запроса
        //В редисе лежит телефон и у него стоит TTL 60 секунд, т.е. если в редисе до сих пор есть
        //этот номер телефона, то 60 секеунд с предыдущего запроса не прошло -> юзеру возвращается ошибка
        if (!otpStore.tryAcquireSendPermit(normalizedPhone, properties.resendCooldown())) {
            Duration retryAfter = otpStore.sendPermitTimeToLive(normalizedPhone);//Смотрим TTL по ключу
            //Возвращаем сообщение об ошибке
            throw new OtpRequestNotAllowedException(retryAfter,
                    "otp.resend-cooldown", retryAfter.toSeconds());
        }
        //Увеличивает количество отправок кода в редисе за определенное окно(ща час времени)
        long sends = otpStore.incrementSendsInWindow(normalizedPhone, properties.window());
        if (sends > properties.maxSendsPerWindow()) {
            //Если отправлено >5, то отклоняем запрос и
            //отдаём реальный остаток окна, а не его полную длину
            //Код не отправили -> отпускаем permit, иначе следующий запрос получит resend-cooldown вместо too-many-requests
            otpStore.releaseSendPermit(normalizedPhone);
            Duration retryAfter = otpStore.sendsWindowTimeToLive(normalizedPhone);
            throw new OtpRequestNotAllowedException(retryAfter,
                    "otp.too-many-requests");
        }

        String code = generateCode(); //Генерируем смс код для входа
        otpStore.save(normalizedPhone, code, properties.ttl());//Сохраняем код в редис

        //Задел под будущую реализацию SMS кодов, сейчас используется просто логирование(LoggingSmsSender)
        smsSender.send(normalizedPhone, SMS_TEMPLATE.formatted(code));

        log.info("OTP issued for phone {}", PhoneNumbersUtils.mask(normalizedPhone));

        //(Временно) возвращаем телефон + код, (НА ПРОДЕ СДЕЛАТЬ 204?)
        return new OtpChallengeDto(
                normalizedPhone,
                //Задел под будущую отправку сообщений, если в .properties файле указано, что код возвращать не надо
                //то в JSON ответа не будет кода, но я думаю, что надо будет возвращать http 204 и пустое тело
                //к проду переделаю
                //TODO
                properties.exposeCode() ? code : null,
                properties.ttl().toSeconds(),
                properties.resendCooldown().toSeconds()
        );
    }

    @Override
    public void verifyCode(String normalizedPhone, String code) {
        //Верифицируем код и получаем ответ в зависимости от входных данных
        OtpVerification verification = otpStore.verify(normalizedPhone, code, properties.maxAttempts());

        switch (verification.outcome()) {
            case OK -> { // Если все норм, то не выбрасываем exception, а идем дальше в вызывающем коде
            }
            //Ниже соответственно ошибки, в зависимости от статуса
            case NOT_REQUESTED -> throw new InvalidOtpException(
                    InvalidOtpException.Reason.NOT_REQUESTED, 0, "otp.not-requested");
            case MISMATCH -> throw new InvalidOtpException(
                    InvalidOtpException.Reason.MISMATCH, verification.attemptsLeft(), "otp.mismatch");
            case ATTEMPTS_EXCEEDED -> throw new InvalidOtpException(
                    InvalidOtpException.Reason.ATTEMPTS_EXCEEDED, 0,
                    "otp.attempts-exceeded");
        }
    }

    //Метод для генерации кода размером, который указан в .properties
    private String generateCode() {
        StringBuilder code = new StringBuilder(properties.length());
        for (int i = 0; i < properties.length(); i++) {
            code.append(secureRandom.nextInt(10));
        }
        return code.toString();
    }

}

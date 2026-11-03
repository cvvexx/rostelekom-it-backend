package io.cvvexxx.users.auth.service;

import io.cvvexxx.users.auth.dto.*;
import io.cvvexxx.users.otp.dto.OtpChallengeDto;
import io.cvvexxx.users.otp.exception.InvalidOtpException;
import io.cvvexxx.users.otp.service.OtpService;
import io.cvvexxx.users.phone.PhoneNumbersUtils;
import io.cvvexxx.users.token.service.TokenService;
import io.cvvexxx.users.user.entity.User;
import io.cvvexxx.users.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service //Бин с бизнес-логикой: Spring создаст один объект и подставит его в AuthController через конструктор
@RequiredArgsConstructor //Конструктор из final-полей, через него Spring передает OtpService, UserService, TokenService
@Slf4j //Создает поле log
public class DefaultAuthService implements AuthService {

    private final OtpService otpService;
    private final UserService userService;
    private final TokenService tokenService;

    @Override
    public OtpChallengeDto requestOtp(OtpRequestDto dto) {
        String phone = PhoneNumbersUtils.normalize(dto.phone());//Приводим пользовательский ввод к нормальному виду через класс-утилиту
        log.info("OTP requested for phone {}", PhoneNumbersUtils.mask(phone));//Логируем для дебага, также скрываем часть номера

        //Ответ одинаковый для нового и зарегистрированного номера, чтобы по нему нельзя было узнать, есть ли юзер в базе
        return otpService.requestCode(phone);
    }

    @Override
    @Transactional//Открываем транзакцию в бд (как async with session.begin()), при RuntimeException будет rollback
    public VerifyResultDto verifyOtp(OtpVerifyDto dto) {
        String phone = PhoneNumbersUtils.normalize(dto.phone());//Приводим пользовательский ввод к нормальному виду через класс-утилиту

        //Сначала проверяем код и только потом смотрим на юзера: без правильного кода ответ одинаковый для любого номера
        otpService.verifyCode(phone, dto.code());//Верифицируем код, если чето не так -> выбрасываем exception и завершаем работу метода

        Optional<User> user = userService.findByPhone(phone);

        //Юзера нет -> номер подтвержден, но надо зарегистрироваться. Выдаем одноразовый токен регистрации,
        //с ним фронт идет на /api/auth/register вместе с ФИО
        if (user.isEmpty()) {
            String token = otpService.issueRegistrationToken(phone);
            log.info("Phone {} verified, registration required", PhoneNumbersUtils.mask(phone));
            return new RegistrationRequiredDto(token, otpService.registrationTtl().toSeconds());
        }

        log.info("User {} authenticated by phone {}", user.get().getId(), PhoneNumbersUtils.mask(phone));//Логируем вход юзера

        return tokenService.issueTokens(user.get());//Возвращаем токены для определенного юзера
    }

    @Override
    @Transactional
    public TokenPairDto registerUser(RegisterDto dto) {
        //Достаем номер по токену регистрации, токен сразу удаляется -> второй раз с ним зарегистрироваться нельзя
        String phone = otpService.consumeRegistrationToken(dto.registrationToken())
                .orElseThrow(() -> new InvalidOtpException(
                        InvalidOtpException.Reason.NOT_REQUESTED, 0, "registration.token-invalid"));

        //Если юзер с этим номером уже успел появиться (две вкладки), createByPhone просто вернет его
        User user = userService.createByPhone(phone, dto.lastName(), dto.firstName(), dto.middleName());
        log.info("User {} registered by phone {}", user.getId(), PhoneNumbersUtils.mask(phone));

        return tokenService.issueTokens(user);//Сразу выдаем токены, чтобы юзеру не пришлось входить заново
    }

    @Override
    public TokenPairDto refresh(String refreshToken) {
        return tokenService.refresh(refreshToken);
    }

    @Override
    public void logout(String refreshToken) {
        tokenService.revoke(refreshToken);
    }

    @Override
    public ServiceTokenResponseDto getServiceToken(ServiceTokenRequestDto dto) {
        return tokenService.issueServiceToken(dto.clientId(), dto.clientSecret());
    }
}

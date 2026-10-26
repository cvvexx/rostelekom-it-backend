package io.cvvexxx.users.auth.service;

import io.cvvexxx.users.auth.dto.OtpRequestDto;
import io.cvvexxx.users.auth.dto.OtpVerifyDto;
import io.cvvexxx.users.auth.dto.ServiceTokenRequestDto;
import io.cvvexxx.users.auth.dto.ServiceTokenResponseDto;
import io.cvvexxx.users.otp.dto.OtpChallengeDto;
import io.cvvexxx.users.otp.service.OtpService;
import io.cvvexxx.users.phone.PhoneNumbersUtils;
import io.cvvexxx.users.token.dto.TokenPairDto;
import io.cvvexxx.users.token.service.TokenService;
import io.cvvexxx.users.user.entity.User;
import io.cvvexxx.users.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        return otpService.requestCode(phone);
    }

    @Override
    @Transactional//Открываем транзакцию в бд (как async with session.begin()), при RuntimeException будет rollback
    public TokenPairDto verifyOtp(OtpVerifyDto dto) {
        String phone = PhoneNumbersUtils.normalize(dto.phone());//Приводим пользовательский ввод к нормальному виду через класс-утилиту
        otpService.verifyCode(phone, dto.code());//Верифицируем код, если чето не так -> выбрасываем exception и завершаем работу метода

        User user = userService.findOrCreateByPhone(phone);//Получаем/создаем юзера по номеру телефона
        log.info("User {} authenticated by phone {}", user.getId(), PhoneNumbersUtils.mask(phone));//Логируем вход юзера

        return tokenService.issueTokens(user);//Возвращаем токены для определенного юзера
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

package io.cvvexxx.users.auth.service;

import io.cvvexxx.users.auth.dto.OtpRequestDto;
import io.cvvexxx.users.auth.dto.OtpVerifyDto;
import io.cvvexxx.users.otp.dto.OtpChallengeDto;
import io.cvvexxx.users.otp.service.OtpService;
import io.cvvexxx.users.phone.PhoneNumbers;
import io.cvvexxx.users.token.dto.TokenPairDto;
import io.cvvexxx.users.token.service.TokenService;
import io.cvvexxx.users.user.entity.User;
import io.cvvexxx.users.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultAuthService implements AuthService {

    private final OtpService otpService;
    private final UserService userService;
    private final TokenService tokenService;

    @Override
    public OtpChallengeDto requestOtp(OtpRequestDto dto) {
        String phone = PhoneNumbers.normalize(dto.phone());//Приводим пользовательский ввод к нормальному виду через класс-утилиту
        log.info("OTP requested for phone {}", PhoneNumbers.mask(phone));//Логируем для дебага, также скрываем часть номера

        return otpService.requestCode(phone);
    }

    @Override
    @Transactional//Открываем транзакцию в бд
    public TokenPairDto verifyOtp(OtpVerifyDto dto) {
        String phone = PhoneNumbers.normalize(dto.phone());//Приводим пользовательский ввод к нормальному виду через класс-утилиту
        otpService.verifyCode(phone, dto.code());//Верифицируем код, если чето не так -> выбрасываем exception и завершаем работу метода

        User user = userService.findOrCreateByPhone(phone);//Получаем/создаем юзера по номеру телефона
        log.info("User {} authenticated by phone {}", user.getId(), PhoneNumbers.mask(phone));//Логируем вход юзера

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
}

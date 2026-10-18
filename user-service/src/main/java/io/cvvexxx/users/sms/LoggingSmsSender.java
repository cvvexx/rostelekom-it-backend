package io.cvvexxx.users.sms;

import io.cvvexxx.users.phone.PhoneNumbersUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LoggingSmsSender implements SmsSender {

    @Override
    public void send(String phone, String message) {
        log.info("[SMS STUB] to={} message={}", PhoneNumbersUtils.mask(phone), message);
    }
}

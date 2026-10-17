package io.cvvexxx.users.sms;

public interface SmsSender {

    void send(String phone, String message);
}

package io.cvvexxx.users.sms;

//Интерфейс, реализация которого отвечает за отправку смс пользователю
public interface SmsSender {

    void send(String phone, String message);
}

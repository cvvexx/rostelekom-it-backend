package io.cvvexxx.users;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

//Spring сканирует этот пакет и все подпакеты, находит классы с @Component/@Service/@RestController/@Configuration
//и сам создает их объекты (бины)
@SpringBootApplication//Основной класс Спринг-приложения, точка входа (как app = FastAPI() + uvicorn.run(app))
@EnableScheduling//Включаем вызов методов по времени(@Scheduled), в FastAPI такого из коробки нет, обычно берут APScheduler
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }

}
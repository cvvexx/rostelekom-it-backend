package io.cvvexxx.services;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@EnableScheduling//Включает фоновую джобу для списаний и продления тарифов
@SpringBootApplication
@EnableMethodSecurity//Включает @PreAuthorize(hasRole('ADMIN')) на методах контроллеров
public class ServicesServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServicesServiceApplication.class, args);
    }

}

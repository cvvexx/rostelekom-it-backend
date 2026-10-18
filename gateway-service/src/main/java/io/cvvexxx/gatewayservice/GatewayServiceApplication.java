package io.cvvexxx.gatewayservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

//Точка входа приложения (как app = FastAPI() + uvicorn.run(app)). Spring сканирует этот пакет и все подпакеты,
//находит классы с @Component/@Service/@RestController/@Configuration и сам создает их объекты (бины)
@SpringBootApplication
public class GatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }

}

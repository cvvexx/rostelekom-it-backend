# rostelekom

Бэкенд на Spring Boot, два сервиса:

- **user-service** (порт `8081`) — юзеры, вход по номеру телефона через SMS-код (OTP), JWT-токены
- **gateway-service** (порт `8080`) — единая точка входа: принимает запросы, проверяет токен и прокидывает их в нужный сервис

## Как запустить

Нужны **Java 21** и **Docker**. Maven ставить не надо, в репе есть `./mvnw`.

```bash
# 1. Создать свой env-файл (один раз)
cp .env.example .env.dev

# 2. Поднять Postgres и Redis
docker compose -f docker-compose.dev.yml up -d

# 3. Запустить user-service (в одном терминале)
./mvnw -pl user-service spring-boot:run -Dspring-boot.run.profiles=dev

# 4. Запустить gateway-service (в другом терминале)
./mvnw -pl gateway-service spring-boot:run -Dspring-boot.run.profiles=dev
```

Профиль `dev` обязателен — без него сервисы не найдут свои настройки.

В профиле `dev` сервисы сами читают `.env.dev` из корня репы (через `spring.config.import` в `application-dev.properties`). Ставить плагин EnvFile и что-то прописывать в Run Configuration не надо. Если `.env.dev` нет, берутся дефолты, и с `docker-compose.dev.yml` всё работает как есть.

Из IntelliJ тоже можно: открыть `UserServiceApplication` / `GatewayServiceApplication`, нажать ▶, а в Run Configuration в поле *Active profiles* вписать `dev`.

Тесты: `./mvnw test`

### Swagger

**http://localhost:8081/swagger-ui.html**

Как потыкать ручки:
1. `POST /api/auth/otp/request` с номером телефона — в dev-режиме код придёт прямо в ответе
2. `POST /api/auth/otp/verify` с номером и кодом — получишь `accessToken`
3. Жмёшь **Authorize** сверху, вставляешь токен — теперь работают `/api/users/me` и остальные закрытые ручки

## Структура папок

```
rostelekom/
├── pom.xml                    # корневой Maven-файл, общий для обоих сервисов
├── docker-compose.dev.yml     # Postgres + Redis для локалки
├── .env.example               # шаблон переменных окружения
│
├── gateway-service/
│   └── src/main/java/.../gatewayservice/
│       ├── config/            # настройки безопасности (проверка JWT)
│       ├── controller/        # заглушка, если user-service упал
│       └── advice/            # обработка ошибок
│
└── user-service/
    └── src/
        ├── main/java/.../users/
        │   ├── auth/          # логин по телефону: запросить код, проверить код, refresh, logout
        │   ├── otp/           # генерация и проверка SMS-кодов (хранятся в Redis)
        │   ├── token/         # выпуск и обновление JWT-токенов
        │   ├── user/          # профиль юзера: /api/users/me
        │   ├── sms/           # отправка SMS (пока просто пишет в лог)
        │   ├── phone/         # нормализация номеров телефона
        │   ├── config/        # общие настройки: безопасность, Swagger
        │   └── advice/        # единая обработка ошибок
        ├── main/resources/
        │   ├── application-dev.properties   # настройки для локалки
        │   └── db/migration/                # SQL-миграции БД (Flyway)
        └── test/              # тесты
```

Код разбит **по фичам**: всё, что относится к, например, токенам, лежит в `token/`. Внутри фичи — папки по слоям (см. ниже).

## Что за что отвечает (спринговые штуки)

Запрос идёт сверху вниз: **Controller → Service → Repository → БД**.

| Папка / штука | Что это | Аналог в FastAPI |
|---|---|---|
| `controller/` | Принимает HTTP-запросы, достаёт данные из запроса и отдаёт их сервису. Логики тут нет | `APIRouter` и `@router.get(...)` |
| `service/` | Бизнес-логика: вся «думалка» живёт тут | функции с логикой, которые зовёт роутер |
| `repository/` | Работа с БД. Обычно это просто интерфейс, SQL Spring пишет сам по имени метода (`findByPhone`) | SQLAlchemy-запросы / CRUD-функции |
| `entity/` | Класс = таблица в БД, поле = колонка | SQLAlchemy-модель |
| `dto/` | Что приходит в запросе и уходит в ответе. Отделяет API от БД | Pydantic-модель |
| `exception/` | Свои ошибки (неверный код, юзер уже есть и т.п.) | `HTTPException` |
| `advice/` | Ловит ошибки со всего приложения и превращает их в нормальный JSON-ответ | `@app.exception_handler` |
| `config/` | Настройки: безопасность, Swagger, бины | создание `app`, middleware |
| `db/migration/` | SQL-файлы `V1__...`, `V2__...`, применяются по порядку при старте | Alembic |

Ещё пара слов, которые будут встречаться:

- **Бин (bean)** — объект, который Spring создаёт сам и держит в одном экземпляре. Классы с `@Service`, `@RestController`, `@Repository`, `@Component` — это бины.
- **Внедрение зависимостей** — ты не пишешь `new UserService()`, а просто объявляешь поле `private final UserService userService;`, и Spring сам его подставит (через конструктор, который генерит `@RequiredArgsConstructor`). Это как `Depends()`.
- **Интерфейс + `Default...`** — например, `UserService` (что умеет) и `DefaultUserService` (как именно). Контроллер знает только про интерфейс, реализацию можно подменить.
- **Lombok** (`@Getter`, `@RequiredArgsConstructor`, `@Slf4j`...) — убирает шаблонный код, генерит геттеры/конструкторы/логгер за тебя.
- **Профиль** (`dev`, `test`) — набор настроек из `application-<профиль>.properties`.

Хочешь добавить новую ручку — делаешь DTO → метод в сервисе → метод в контроллере. Нужна новая таблица — новый файл миграции `V<следующий номер>__описание.sql` + entity + repository. Старые миграции не трогаем.

# rostelekom

Бэкенд на Spring Boot, четыре сервиса:

- **user-service** (порт `8081`) — юзеры, вход по номеру телефона через SMS-код (OTP), JWT-токены
- **billing-service** (порт `8082`) — лицевой счёт, баланс, история операций, пополнение
- **services-service** (порт `8083`) — тарифы и подписки, ежемесячное списание за подписки через billing
- **gateway-service** (порт `8080`) — единая точка входа: принимает запросы, проверяет токен и прокидывает их в нужный сервис

## Как запустить

Нужны **Java 21+** и **Docker**. Maven ставить не надо, в репе есть `./mvnw`.

```bash
# 1. Создать свой env-файл (один раз)
cp .env.example .env.dev

# 2. Поднять Postgres и Redis
docker compose -f docker-compose.dev.yml --env-file .env.dev up -d

# 3. Запустить user-service (в одном терминале)
./mvnw -pl user-service spring-boot:run "-Dspring-boot.run.profiles=dev"

# 4. Запустить billing-service (во втором терминале)
./mvnw -pl billing-service spring-boot:run "-Dspring-boot.run.profiles=dev"

# 5. Запустить services-service (в третьем терминале)
./mvnw -pl services-service spring-boot:run "-Dspring-boot.run.profiles=dev"

# 6. Запустить gateway-service (в четвёртом терминале)
./mvnw -pl gateway-service spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Профиль `dev` обязателен — без него сервисы не найдут свои настройки.

В профиле `dev` сервисы сами читают `.env.dev` из корня репы (через `spring.config.import` в `application-dev.properties`). Ставить плагин EnvFile и что-то прописывать в Run Configuration не надо. Если `.env.dev` нет, берутся дефолты, и с `docker-compose.dev.yml` всё работает как есть.

Из IntelliJ тоже можно: открыть `UserServiceApplication` / `BillingServiceApplication` / `ServicesServiceApplication` / `GatewayServiceApplication`, нажать ▶, а в Run Configuration в поле *Active profiles* вписать `dev`.

Тесты: `./mvnw test`

### Swagger

- user-service: **http://localhost:8081/swagger-ui.html**
- billing-service: **http://localhost:8082/swagger-ui.html**
- services-service: **http://localhost:8083/swagger-ui.html**

Swagger открывается напрямую на сервисе, а не через gateway. Спека в JSON лежит по `/v3/api-docs` на тех же портах, из неё можно сгенерировать клиент для фронта.

Как потыкать ручки:
1. `POST /api/auth/otp/request` с номером телефона — в dev-режиме код придёт прямо в ответе
2. `POST /api/auth/otp/verify` с номером и кодом — получишь `accessToken`
3. Жмёшь **Authorize** сверху, вставляешь токен — теперь работают `/api/users/me` и остальные закрытые ручки
4. Тот же токен подходит и для Swagger billing-service и services-service

## API для фронта

Все запросы идут на gateway: **http://localhost:8080**. Закрытые ручки требуют заголовок `Authorization: Bearer <accessToken>`.

### Вход и регистрация

Вход и регистрация начинаются одинаково: запросить код и подтвердить его. Узнать, новый номер или нет, можно только после правильного кода, иначе по ответам можно было бы перебирать чужие номера.

| Запрос | Тело | Ответ |
|---|---|---|
| `POST /api/auth/otp/request` | `{ "phone": "+79991234567" }` | `{ phone, code, expiresInSeconds, resendAfterSeconds }` — `code` приходит только в dev |
| `POST /api/auth/otp/verify` | `{ "phone", "code" }` | номер зарегистрирован: `200 { accessToken, refreshToken, tokenType, expiresInSeconds, userId }`<br>номер новый: `202 { registrationToken, expiresInSeconds }` |
| `POST /api/auth/register` | `{ "registrationToken", "lastName", "firstName", "middleName" }` | `201`, тело как у `200` в `/otp/verify` |
| `POST /api/auth/refresh` | `{ "refreshToken" }` | новая пара токенов |
| `POST /api/auth/logout` | `{ "refreshToken" }` | `204` |

- Access-токен живёт 15 минут, refresh — 30 дней.
- **Refresh одноразовый.** После `/refresh` сохраняй новую пару и выбрасывай старую. Повторное использование старого refresh завершает **все** сессии юзера. Если фронт открыт в нескольких вкладках, refresh должен делать кто-то один, иначе юзера будет выкидывать.
- **Регистрация.** Если `/otp/verify` вернул `202`, номер новый: покажи форму ФИО и отправь её в `/register` вместе с `registrationToken`. `lastName` и `firstName` обязательны (до 100 символов), `middleName` — по желанию. Ошибка валидации (`400` с `errors`) токен не сжигает, можно поправить поля и отправить снова.
- **`registrationToken` одноразовый и живёт `expiresInSeconds` (10 минут).** Если он истёк или уже использован, `/register` вернёт `400` с `reason: NOT_REQUESTED`: отправь юзера заново запрашивать код.
- Повторно запросить код можно через `resendAfterSeconds`. При частых запросах придёт `429` с заголовком `Retry-After`.

### Профиль

| Запрос | Тело | Ответ |
|---|---|---|
| `GET /api/users/me` | — | `{ id, phone, lastName, firstName, middleName }` |

Профиль только для чтения: ФИО задаётся при регистрации.

### Счёт и баланс

| Запрос | Ответ |
|---|---|
| `GET /api/billing/accounts/me` | `{ id, number, balance, currency, status }` |
| `POST /api/billing/accounts` | открыть счёт: `201` если создан, `200` если уже был, тело как у `GET /me` |
| `GET /api/billing/accounts/me/transactions?page=0&size=20` | страница операций, новые сверху |

- Пока счёта нет, `GET /me`, платежи и история отвечают `404`. Предложи юзеру открыть счёт и вызови `POST /accounts`.
- `status`: `ACTIVE`, `BLOCKED` (ушёл в минус или заблокирован — пополнять можно), `CLOSED`.

### Пополнение баланса

| Запрос | Тело | Ответ |
|---|---|---|
| `POST /api/billing/payments` | `{ "amount": 500.00 }` + заголовок `Idempotency-Key` | `{ id, amount, currency, status, confirmationUrl, createdAt }` |
| `GET /api/billing/payments/{id}` | — | платёж |
| `GET /api/billing/payments?page=0&size=20` | — | страница платежей |
| `POST /api/billing/payments/{id}/cancel` | — | отменённый платёж |

- `Idempotency-Key` обязателен (до 64 символов). Генерируй новый UUID на каждое нажатие «Пополнить» и повторяй тот же ключ при ретрае, тогда двойного платежа не будет.
- `status`: `PENDING` (ждём оплату), `SUCCEEDED` (деньги зачислены), `CANCELED`.
- **Провайдер выбирается в `.env.dev` переменной `PAYMENT_PROVIDER`.**
  - `stub` (по умолчанию, ключи не нужны): `confirmationUrl` — путь вида `/api/billing/stub/pay/stub-<id>`, на него надо отправить `POST` с токеном. После этого платёж станет `SUCCEEDED`, а баланс увеличится.
  - `yookassa` (тестовый магазин ЮKassa, нужны `YOOKASSA_*` в `.env.dev`): `confirmationUrl` — страница оплаты ЮKassa, фронт делает на неё редирект. После оплаты ЮKassa вернёт юзера на `YOOKASSA_RETURN_URL`, а деньги зачислятся по webhook'у `POST /api/billing/yookassa/webhook`. Фронту нужно просто перезапросить платёж или баланс. Тестовая карта: `5555 5555 5555 4477`.

### Тарифы

| Запрос | Тело | Ответ |
|---|---|---|
| `GET /api/services/tariffs?category=INTERNET&page=0&size=20` | — | страница активных тарифов, **без токена** |
| `GET /api/services/tariffs/{id}` | — | `{ id, name, description, category, price, currency, billingPeriod, active, createdAt }`, **без токена** |

- `category`: `INTERNET`, `TV`, `MOBILE`, параметр необязательный.
- Сортировка: `sort=createdAt|price|name|category,asc|desc`.
- Управление тарифами (`GET /all`, `POST`, `PATCH /{id}`, `DELETE /{id}` — архивация) только для `ADMIN`.

### Подписки

| Запрос | Тело | Ответ |
|---|---|---|
| `POST /api/services/subscriptions` | `{ "tariffId": "..." }` | `201` + подписка, первый месяц списывается сразу |
| `GET /api/services/subscriptions/my?page=0&size=20` | — | страница своих подписок, новые сверху |
| `GET /api/services/subscriptions/{id}` | — | подписка |
| `POST /api/services/subscriptions/{id}/cancel?immediate=false` | — | подписка после отмены |

- Подписка: `{ id, tariffId, tariffName, tariffCategory, status, price, currency, billingPeriod, currentPeriodStart, nextChargeAt, cancelAtPeriodEnd, canceledAt, createdAt, ... }`.
- `status`: `ACTIVE`, `SUSPENDED` (не хватило денег, сервис сам повторит списание; если не оплатить за 30 дней — подписка отменится), `CANCELED`.
- Нужен открытый счёт с деньгами. Если денег не хватило или счёта нет, `POST` вернёт `409`, подписка не создаётся. На один тариф может быть только одна активная подписка, повтор — тоже `409`.
- Отмена по умолчанию в конце оплаченного периода (`cancelAtPeriodEnd: true`), `immediate=true` — сразу, деньги за текущий период не возвращаются.
- Списание за следующий месяц сервис делает сам раз в `nextChargeAt`.

### Ошибки

Все ошибки приходят в формате [ProblemDetail](https://www.rfc-editor.org/rfc/rfc9457):

```json
{ "status": 400, "title": "Bad Request", "detail": "Ошибка валидации", "errors": { "amount": "Сумма платежа должна быть больше нуля" } }
```

`detail` — готовый текст на русском, его можно показывать юзеру. `errors` есть только у ошибок валидации (ключ — поле запроса). Если сервис за gateway лежит, придёт `503`. Если запросов больше 100 в минуту (на юзера, а без токена на IP), gateway ответит `429` с заголовком `Retry-After`.

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

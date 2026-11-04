package io.cvvexxx.billings.payment.provider.yookassa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.cvvexxx.billings.payment.provider.PaymentProvider;
import io.cvvexxx.billings.payment.provider.ProviderPayment;
import io.cvvexxx.billings.payment.provider.ProviderStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

//Создаем бин только если в пропертях есть соответствующая настройка
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "yookassa")
public class YooKassaPaymentProvider implements PaymentProvider {

    //Таймауты для рест клиента
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    private final RestClient restClient;
    private final String returnUrl;

    //Основной конструктор, второй для тестов
    @Autowired
    public YooKassaPaymentProvider(
            @Value("${app.payment.yookassa.base-url:https://api.yookassa.ru/v3}") String baseUrl,
            @Value("${app.payment.yookassa.shop-id}") String shopId,
            @Value("${app.payment.yookassa.secret-key}") String secretKey,
            @Value("${app.payment.yookassa.return-url}") String returnUrl
    ) {
        this(
                RestClient.builder()
                        .baseUrl(baseUrl)
                        .defaultHeaders(h -> h.setBasicAuth(shopId, secretKey))
                        .requestFactory(requestFactory()),
                returnUrl
        );
    }

    //два разных конструктора, этот для тестов
    YooKassaPaymentProvider(RestClient.Builder restClientBuilder, String returnUrl) {
        this.restClient = restClientBuilder.build();
        this.returnUrl = returnUrl;
    }

    @Override
    public String name() {
        return "YOOKASSA";
    }

    //Метод создания платежа в юкассе. idempotencyKey от фронта сюда не шлём: он уникален только в пределах счёта,
    //а юкассе нужна уникальность в пределах магазина. Вместо него Idempotence-Key = paymentId: ключ фронта у нас в бд
    //всегда даёт один и тот же paymentId, так что повтор запроса не создаст второй платёж в юкассе
    @Override
    public ProviderPayment create(UUID paymentId, BigDecimal amount, String currency, String idempotencyKey) {
        var request = new CreateRequest(
                new Amount(amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString(), currency),
                true,
                new ConfirmationRequest("redirect", returnUrl),
                "Пополнение счёта",
                Map.of("paymentId", paymentId.toString())
        );

        PaymentResponse response = restClient.post()
                .uri("/payments")
                .header("Idempotence-Key", paymentId.toString())
                .body(request)
                .retrieve()
                .body(PaymentResponse.class);

        return new ProviderPayment(response.id(), response.confirmation().confirmationUrl());
    }

    //Спрашиваем статус у юкассы
    @Override
    public ProviderStatus fetchStatus(String providerPaymentId) {
        PaymentResponse response = restClient.get()
                .uri("/payments/{id}", providerPaymentId)
                .retrieve()
                .body(PaymentResponse.class);

        return toProviderStatus(response.status());
    }

    //Отменить pending-платёж через API юкассы нельзя (при capture=true /cancel работает только для waiting_for_capture),
    //неоплаченный она отменит сама по истечении срока. Поэтому просто возвращаем текущий статус у неё:
    //SUCCEEDED - юзер успел оплатить и деньги зачислятся, CANCELED - отменяем и у нас, PENDING - пока отменить нельзя
    @Override
    public ProviderStatus cancel(String providerPaymentId) {
        return fetchStatus(providerPaymentId);
    }

    //Преобразуем статус юкассы в наш enum
    static ProviderStatus toProviderStatus(String status) {
        return switch (status) {
            case "pending", "waiting_for_capture" -> ProviderStatus.PENDING;
            case "succeeded" -> ProviderStatus.SUCCEEDED;
            case "canceled" -> ProviderStatus.CANCELED;
            default -> throw new IllegalStateException("Неизвестный статус платежа ЮKassa: " + status);
        };
    }

    //Выставляем таймауты для рест клиента
    private static JdkClientHttpRequestFactory requestFactory() {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    //Ниже Dto
    private record CreateRequest(
            Amount amount,
            boolean capture,
            ConfirmationRequest confirmation,
            String description,
            Map<String, String> metadata
    ) {
    }

    private record Amount(
            String value,
            String currency
    ) {
    }

    private record ConfirmationRequest(
            String type,
            @JsonProperty("return_url") String returnUrl
    ) {
    }

    //Если в ответе есть какие-то дополнительные поля, то их игнорируем
    @JsonIgnoreProperties(ignoreUnknown = true)
    record PaymentResponse(
            String id,
            String status,
            ConfirmationResponse confirmation
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ConfirmationResponse(
            @JsonProperty("confirmation_url") String confirmationUrl
    ) {
    }
}

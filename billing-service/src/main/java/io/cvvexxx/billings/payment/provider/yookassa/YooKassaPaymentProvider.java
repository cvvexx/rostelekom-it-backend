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

@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "yookassa")
public class YooKassaPaymentProvider implements PaymentProvider {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    private final RestClient restClient;
    private final String returnUrl;

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

    //idempotencyKey не используем: у нас он уникален в пределах счёта, а у ЮKassa - в пределах всего магазина.
    //paymentId тоже стабилен при повторах запроса с тем же ключом, поэтому идемпотентность сохраняется
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

    @Override
    public ProviderStatus fetchStatus(String providerPaymentId) {
        PaymentResponse response = restClient.get()
                .uri("/payments/{id}", providerPaymentId)
                .retrieve()
                .body(PaymentResponse.class);

        return toProviderStatus(response.status());
    }

    @Override
    public ProviderStatus cancel(String providerPaymentId) {
        return fetchStatus(providerPaymentId);
    }

    static ProviderStatus toProviderStatus(String status) {
        return switch (status) {
            case "pending", "waiting_for_capture" -> ProviderStatus.PENDING;
            case "succeeded" -> ProviderStatus.SUCCEEDED;
            case "canceled" -> ProviderStatus.CANCELED;
            default -> throw new IllegalStateException("Неизвестный статус платежа ЮKassa: " + status);
        };
    }

    private static JdkClientHttpRequestFactory requestFactory() {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

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

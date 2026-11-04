package io.cvvexxx.billings.payment.provider.yookassa;

import io.cvvexxx.billings.payment.provider.ProviderPayment;
import io.cvvexxx.billings.payment.provider.ProviderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class YooKassaPaymentProviderTest {

    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://yookassa.test/v3");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final YooKassaPaymentProvider provider = new YooKassaPaymentProvider(builder, "https://app.test/return");

    @Test
    @DisplayName("create: Idempotence-Key = наш paymentId, сумма с 2 знаками, из ответа берём id и ссылку")
    void create() {
        UUID paymentId = UUID.randomUUID();
        server.expect(requestTo("https://yookassa.test/v3/payments"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotence-Key", paymentId.toString()))
                .andExpect(jsonPath("$.amount.value").value("100.00"))
                .andExpect(jsonPath("$.amount.currency").value("RUB"))
                .andExpect(jsonPath("$.capture").value(true))
                .andExpect(jsonPath("$.confirmation.return_url").value("https://app.test/return"))
                .andExpect(jsonPath("$.metadata.paymentId").value(paymentId.toString()))
                .andRespond(withSuccess("""
                        {"id":"yk-1","status":"pending","paid":false,
                         "confirmation":{"type":"redirect","confirmation_url":"https://yoomoney.ru/pay/yk-1"}}
                        """, MediaType.APPLICATION_JSON));

        ProviderPayment payment = provider.create(paymentId, new BigDecimal("100"), "RUB", "client-key");

        assertThat(payment).isEqualTo(new ProviderPayment("yk-1", "https://yoomoney.ru/pay/yk-1"));
        server.verify();
    }

    @Test
    @DisplayName("fetchStatus и cancel: статус ЮKassa мапится в ProviderStatus")
    void fetchStatusAndCancel() {
        server.expect(requestTo("https://yookassa.test/v3/payments/yk-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":\"yk-1\",\"status\":\"succeeded\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://yookassa.test/v3/payments/yk-2"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":\"yk-2\",\"status\":\"pending\"}", MediaType.APPLICATION_JSON));

        assertThat(provider.fetchStatus("yk-1")).isEqualTo(ProviderStatus.SUCCEEDED);
        assertThat(provider.cancel("yk-2")).isEqualTo(ProviderStatus.PENDING);//pending ЮKassa через API не отменяет
        assertThat(YooKassaPaymentProvider.toProviderStatus("waiting_for_capture")).isEqualTo(ProviderStatus.PENDING);
        assertThat(YooKassaPaymentProvider.toProviderStatus("canceled")).isEqualTo(ProviderStatus.CANCELED);
        server.verify();
    }
}

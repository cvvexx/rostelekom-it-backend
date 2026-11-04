package io.cvvexxx.billings.payment.provider.yookassa;

import io.cvvexxx.billings.payment.exception.PaymentNotFoundException;
import io.cvvexxx.billings.payment.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class YooKassaWebhookControllerTest {

    private final PaymentService paymentService = mock(PaymentService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new YooKassaWebhookController(paymentService)).build();

    @Test
    @DisplayName("Уведомление передаёт id платежа в сервис, неизвестный платёж - тоже 200, чтобы ЮKassa не ретраила")
    void handle() throws Exception {
        when(paymentService.handleProviderNotification("yk-unknown"))
                .thenThrow(new PaymentNotFoundException("payment.not-found-by-provider", "YOOKASSA", "yk-unknown"));

        mockMvc.perform(post(YooKassaWebhookController.PATH).contentType(MediaType.APPLICATION_JSON)
                        .content(body("yk-1")))
                .andExpect(status().isOk());
        mockMvc.perform(post(YooKassaWebhookController.PATH).contentType(MediaType.APPLICATION_JSON)
                        .content(body("yk-unknown")))
                .andExpect(status().isOk());

        verify(paymentService).handleProviderNotification("yk-1");
    }

    private static String body(String id) {
        return """
                {"type":"notification","event":"payment.succeeded",
                 "object":{"id":"%s","status":"succeeded","amount":{"value":"100.00","currency":"RUB"}}}
                """.formatted(id);
    }
}

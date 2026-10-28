package io.cvvexxx.billings.payment.provider.stub;

import io.cvvexxx.billings.payment.dto.PaymentDto;
import io.cvvexxx.billings.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

//На проде вместо этого класса будет контроллер, который будет обрабатывать webhook о том, что юзер оплатил от провайдера
//Существует только вместе со StubPaymentProvider, на проде с реальным провайдером этого эндпоинта нет
@RestController
@RequiredArgsConstructor
@RequestMapping(StubPaymentController.PATH)
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "stub")
public class StubPaymentController {

    static final String PATH = "/api/billing/stub/pay";

    private final StubPaymentProvider stubPaymentProvider;
    private final PaymentService paymentService;

    //Эндпоинт имитации подтверждения оплаты, вызывает фронт
    //То что любой может пополнить свой или чужой счет это толкьо для dev, фиксить впадлу если честно
    @PostMapping("/{providerPaymentId}")
    public ResponseEntity<PaymentDto> pay(@PathVariable("providerPaymentId") String providerPaymentId) {
        stubPaymentProvider.markPaid(providerPaymentId);//имитация оплаты
        //На проде метод ниже должен вызывать провайдер оплаты
        return ResponseEntity.ok(paymentService.handleProviderNotification(providerPaymentId));
    }
}

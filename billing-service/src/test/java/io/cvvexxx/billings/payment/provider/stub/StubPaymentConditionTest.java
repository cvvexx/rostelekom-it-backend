package io.cvvexxx.billings.payment.provider.stub;

import io.cvvexxx.billings.payment.provider.PaymentProvider;

import io.cvvexxx.billings.payment.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class StubPaymentConditionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(StubPaymentProvider.class, StubPaymentController.class)
            .withBean(PaymentService.class, () -> mock(PaymentService.class));

    @Test
    @DisplayName("app.payment.provider=stub: есть заглушка провайдера и её эндпоинт оплаты")
    void stub_registersProviderAndController() {
        runner.withPropertyValues("app.payment.provider=stub").run(context -> {
            assertThat(context).hasSingleBean(PaymentProvider.class);
            assertThat(context.getBean(PaymentProvider.class)).isInstanceOf(StubPaymentProvider.class);
            assertThat(context).hasSingleBean(StubPaymentController.class);
        });
    }

    @Test
    @DisplayName("Свойство не задано: ни заглушки, ни бесплатной оплаты — сервис не стартует без PaymentProvider")
    void missingProperty_registersNothing() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(PaymentProvider.class);
            assertThat(context).doesNotHaveBean(StubPaymentController.class);
        });
    }

    @Test
    @DisplayName("Другой провайдер (yookassa): эндпоинт заглушки не поднимается")
    void otherProvider_doesNotRegisterStub() {
        runner.withPropertyValues("app.payment.provider=yookassa").run(context -> {
            assertThat(context).doesNotHaveBean(StubPaymentProvider.class);
            assertThat(context).doesNotHaveBean(StubPaymentController.class);
        });
    }
}

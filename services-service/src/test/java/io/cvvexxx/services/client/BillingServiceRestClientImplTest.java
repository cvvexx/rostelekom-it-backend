package io.cvvexxx.services.client;

import io.cvvexxx.services.subscription.dto.CreateChargeDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class BillingServiceRestClientImplTest {

    private static final CreateChargeDto CHARGE =
            new CreateChargeDto(UUID.randomUUID(), new BigDecimal("499.00"), "sub:1", "Продление тарифа");

    private MockRestServiceServer billing;
    private BillingServiceRestClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://billing");
        billing = MockRestServiceServer.bindTo(builder).build();
        client = new BillingServiceRestClientImpl(builder.build());
    }

    @Test
    void charged_sendsBodyAndReturnsTransactionId() {
        UUID transactionId = UUID.randomUUID();
        billing.expect(requestTo("http://billing/internal/billing/charges"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.idempotencyKey").value("sub:1"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("""
                        {"transactionId": "%s", "balanceAfter": 1.00, "createdAt": "2026-11-01T12:00:00Z"}
                        """.formatted(transactionId)));

        assertThat(client.createCharge(CHARGE)).isEqualTo(new ChargeOutcome.Charged(transactionId));
    }

    @Test
    void businessErrors_mappedByCode() {
        expectProblem(HttpStatus.CONFLICT, "charge.insufficient-funds");
        expectProblem(HttpStatus.CONFLICT, "charge.account-blocked");
        expectProblem(HttpStatus.NOT_FOUND, "account.not-found-by-user");
        expectProblem(HttpStatus.CONFLICT, "charge.account-closed");

        assertThat(client.createCharge(CHARGE)).isEqualTo(new ChargeOutcome.Declined("charge.insufficient-funds"));
        assertThat(client.createCharge(CHARGE)).isEqualTo(new ChargeOutcome.Declined("charge.account-blocked"));
        assertThat(client.createCharge(CHARGE)).isEqualTo(new ChargeOutcome.Declined("account.not-found-by-user"));
        assertThat(client.createCharge(CHARGE)).isEqualTo(new ChargeOutcome.AccountClosed());
    }

    @Test
    void unknownErrors_thrown() {
        billing.expect(requestTo("http://billing/internal/billing/charges"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));//404 без ProblemDetail - кривой url, а не нет счёта
        expectProblem(HttpStatus.CONFLICT, "something.new");
        billing.expect(requestTo("http://billing/internal/billing/charges")).andRespond(withServerError());

        assertThatThrownBy(() -> client.createCharge(CHARGE)).isInstanceOf(HttpClientErrorException.NotFound.class);
        assertThatThrownBy(() -> client.createCharge(CHARGE)).isInstanceOf(HttpClientErrorException.Conflict.class);
        assertThatThrownBy(() -> client.createCharge(CHARGE)).isInstanceOf(HttpServerErrorException.class);
    }

    private void expectProblem(HttpStatus status, String code) {
        billing.expect(requestTo("http://billing/internal/billing/charges"))
                .andRespond(withStatus(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body("""
                        {"type": "about:blank", "status": %d, "detail": "текст", "code": "%s"}
                        """.formatted(status.value(), code)));
    }
}

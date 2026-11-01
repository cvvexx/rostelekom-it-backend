package io.cvvexxx.services.client;

import io.cvvexxx.services.subscription.dto.ChargeDto;
import io.cvvexxx.services.subscription.dto.CreateChargeDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;

@RequiredArgsConstructor
public class BillingServiceRestClientImpl implements BillingServiceRestClient {

    //Коды из messages.properties billing-service, приходят в поле code у ProblemDetail
    private static final String INSUFFICIENT_FUNDS_CODE = "charge.insufficient-funds";
    private static final String ACCOUNT_BLOCKED_CODE = "charge.account-blocked";
    private static final String ACCOUNT_NOT_FOUND_CODE = "account.not-found-by-user";
    private static final String ACCOUNT_CLOSED_CODE = "charge.account-closed";

    private final RestClient restClient;

    @Override
    public ChargeOutcome createCharge(CreateChargeDto createChargeDto) {
        try {
            ChargeDto charge = restClient
                    .post()
                    .uri("/internal/billing/charges")
                    .body(createChargeDto)
                    .retrieve()
                    .body(ChargeDto.class);
            return new ChargeOutcome.Charged(charge.transactionId());
        } catch (HttpClientErrorException.Conflict | HttpClientErrorException.NotFound e) {
            String code = problemCode(e);
            return switch (code) {
                case INSUFFICIENT_FUNDS_CODE, ACCOUNT_BLOCKED_CODE, ACCOUNT_NOT_FOUND_CODE -> new ChargeOutcome.Declined(code);
                case ACCOUNT_CLOSED_CODE -> new ChargeOutcome.AccountClosed();
                case null, default -> throw e;
            };
        }
    }

    private static String problemCode(HttpClientErrorException e) {
        ProblemDetail problem = e.getResponseBodyAs(ProblemDetail.class);
        Map<String, Object> properties = problem == null ? null : problem.getProperties();
        return properties != null && properties.get("code") instanceof String code ? code : null;
    }
}

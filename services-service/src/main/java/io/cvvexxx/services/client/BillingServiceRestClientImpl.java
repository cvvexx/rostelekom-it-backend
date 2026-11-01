package io.cvvexxx.services.client;

import io.cvvexxx.services.subscription.dto.ChargeDto;
import io.cvvexxx.services.subscription.dto.CreateChargeDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.client.RestClient;

@RequiredArgsConstructor
public class BillingServiceRestClientImpl implements BillingServiceRestClient {

    private final RestClient restClient;

    @Override
    public ChargeDto createCharge(CreateChargeDto createChargeDto) {
        return null;
    }
}

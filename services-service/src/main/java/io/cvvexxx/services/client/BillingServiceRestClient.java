package io.cvvexxx.services.client;

import io.cvvexxx.services.subscription.dto.CreateChargeDto;

public interface BillingServiceRestClient {

    ChargeOutcome createCharge(CreateChargeDto createChargeDto);

}

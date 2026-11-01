package io.cvvexxx.services.client;

import io.cvvexxx.services.subscription.dto.ChargeDto;
import io.cvvexxx.services.subscription.dto.CreateChargeDto;

public interface BillingServiceRestClient {

    ChargeDto createCharge(CreateChargeDto createChargeDto);

}

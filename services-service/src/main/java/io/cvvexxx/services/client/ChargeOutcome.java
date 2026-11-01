package io.cvvexxx.services.client;

import java.util.UUID;

public interface ChargeOutcome {

    record Charged(UUID transactionId) implements ChargeOutcome {
    }

    record Declined(String code) implements ChargeOutcome {
    }

    record AccountClosed() implements ChargeOutcome {
    }
}

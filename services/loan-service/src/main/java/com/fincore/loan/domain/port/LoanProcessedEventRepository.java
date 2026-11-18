package com.fincore.loan.domain.port;

import java.util.UUID;

public interface LoanProcessedEventRepository {

    boolean claim(UUID eventId, String eventType);
}

package com.fincore.ledger.domain.port;

import java.util.UUID;

public interface ProcessedEventRepository {

    boolean claim(UUID eventId, String eventType);
}

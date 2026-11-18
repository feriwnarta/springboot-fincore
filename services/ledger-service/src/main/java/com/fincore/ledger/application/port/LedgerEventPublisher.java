package com.fincore.ledger.application.port;

import com.fincore.ledger.application.query.LedgerOutboxMessage;

public interface LedgerEventPublisher {

    void publish(LedgerOutboxMessage message);
}

package com.fincore.ledger.domain.port;

import com.fincore.ledger.domain.model.LedgerOutboxEntry;

public interface LedgerOutboxRepository {

    void append(LedgerOutboxEntry event);
}

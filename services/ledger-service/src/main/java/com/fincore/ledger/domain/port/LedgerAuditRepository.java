package com.fincore.ledger.domain.port;

import com.fincore.platform.audit.AuditEvent;

public interface LedgerAuditRepository {

    void append(AuditEvent event);
}

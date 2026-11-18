package com.fincore.loan.domain.port;

import com.fincore.loan.domain.model.LoanAuditEntry;

public interface LoanAuditRepository {

    void append(LoanAuditEntry entry);
}

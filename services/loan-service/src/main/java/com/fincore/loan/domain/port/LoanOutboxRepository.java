package com.fincore.loan.domain.port;

import com.fincore.loan.domain.model.LoanOutboxEntry;

public interface LoanOutboxRepository {

    void append(LoanOutboxEntry entry);
}

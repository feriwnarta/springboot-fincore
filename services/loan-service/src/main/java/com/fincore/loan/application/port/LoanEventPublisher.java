package com.fincore.loan.application.port;

import com.fincore.loan.application.query.LoanOutboxMessage;

public interface LoanEventPublisher {

    void publish(LoanOutboxMessage message) throws Exception;
}

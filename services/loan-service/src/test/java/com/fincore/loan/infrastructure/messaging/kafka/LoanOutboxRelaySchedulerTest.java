package com.fincore.loan.infrastructure.messaging.kafka;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fincore.loan.application.service.LoanOutboxRelay;

import org.junit.jupiter.api.Test;

class LoanOutboxRelaySchedulerTest {

    @Test
    void delegatesScheduledInvocationToRelay() {
        LoanOutboxRelay relay = mock(LoanOutboxRelay.class);
        LoanOutboxRelayScheduler scheduler = new LoanOutboxRelayScheduler(relay);

        scheduler.relay();

        verify(relay).runOnce();
    }
}

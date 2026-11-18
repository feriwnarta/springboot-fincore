package com.fincore.loan.infrastructure.messaging.kafka;

import com.fincore.loan.application.service.LoanOutboxRelay;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "fincore.kafka.outbox-relay.enabled",
        havingValue = "true")
public class LoanOutboxRelayScheduler {

    private final LoanOutboxRelay relay;

    @Scheduled(fixedDelayString = "${fincore.kafka.outbox-relay.fixed-delay:1000ms}")
    void relay() {
        relay.runOnce();
    }
}

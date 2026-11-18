package com.fincore.ledger.infrastructure.messaging.kafka;

import com.fincore.ledger.application.service.LedgerOutboxRelay;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "fincore.kafka.outbox-relay.enabled", havingValue = "true")
public class LedgerOutboxRelayScheduler {

    private final LedgerOutboxRelay relay;

    @Scheduled(fixedDelayString = "${fincore.kafka.outbox-relay.fixed-delay:1000ms}")
    void relay() {
        relay.runOnce();
    }
}

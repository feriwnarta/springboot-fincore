package com.fincore.ledger.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.fincore.ledger.application.port.LedgerEventPublisher;
import com.fincore.ledger.application.port.LedgerOutboxRelayRepository;
import com.fincore.ledger.application.query.LedgerOutboxMessage;
import com.fincore.ledger.config.LedgerOutboxRelayProperties;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LedgerOutboxRelay {

    private static final Logger LOGGER = LoggerFactory.getLogger(LedgerOutboxRelay.class);
    private static final int MAX_ERROR_LENGTH = 1_000;

    private final LedgerOutboxRelayRepository repository;
    private final LedgerEventPublisher publisher;
    private final LedgerOutboxRelayProperties properties;
    private final Clock clock;

    public void runOnce() {
        if (!properties.enabled()) {
            return;
        }
        Instant now = Instant.now(clock);
        UUID claimToken = UUID.randomUUID();
        for (LedgerOutboxMessage message : repository.claimBatch(
                properties.batchSize(), now, properties.claimLease(), claimToken)) {
            publishOne(message);
        }
    }

    private void publishOne(LedgerOutboxMessage message) {
        try {
            publisher.publish(message);
            repository.markPublished(message.eventId(), message.claimToken(), Instant.now(clock));
            LOGGER.info("ledger_outbox_published eventId={} eventType={} businessReference={}",
                    message.eventId(), message.eventType(), message.businessReference());
        } catch (Exception exception) {
            String error = exception.getMessage();
            if (error == null || error.isBlank()) {
                error = exception.getClass().getSimpleName();
            }
            repository.markFailed(message.eventId(), message.claimToken(), Instant.now(clock),
                    error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH));
            LOGGER.error("ledger_outbox_publish_failed eventId={} eventType={} businessReference={}",
                    message.eventId(), message.eventType(), message.businessReference(), exception);
        }
    }
}

package com.fincore.loan.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.fincore.loan.application.port.LoanEventPublisher;
import com.fincore.loan.application.port.LoanOutboxRelayRepository;
import com.fincore.loan.application.query.LoanOutboxMessage;
import com.fincore.loan.config.LoanOutboxRelayProperties;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoanOutboxRelay {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoanOutboxRelay.class);
    private static final int MAX_ERROR_LENGTH = 1_000;

    private final LoanOutboxRelayRepository repository;
    private final LoanEventPublisher publisher;
    private final LoanOutboxRelayProperties properties;
    private final Clock clock;

    public void runOnce() {
        if (!properties.enabled()) {
            return;
        }

        Instant now = Instant.now(clock);
        UUID claimToken = UUID.randomUUID();
        var messages = repository.claimBatch(
                properties.batchSize(), now, properties.claimLease(), claimToken);
        for (LoanOutboxMessage message : messages) {
            publishOne(message);
        }
    }

    private void publishOne(LoanOutboxMessage message) {
        try {
            publisher.publish(message);
            repository.markPublished(message.eventId(), message.claimToken(), Instant.now(clock));
            LOGGER.info("loan_outbox_published eventId={} eventType={} businessReference={}",
                    message.eventId(), message.eventType(), message.businessReference());
        } catch (Exception exception) {
            String errorMessage = boundedErrorMessage(exception);
            repository.markFailed(message.eventId(), message.claimToken(), Instant.now(clock), errorMessage);
            LOGGER.error("loan_outbox_publish_failed eventId={} eventType={} businessReference={}",
                    message.eventId(), message.eventType(), message.businessReference());
        }
    }

    private static String boundedErrorMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return message.length() <= MAX_ERROR_LENGTH
                ? message
                : message.substring(0, MAX_ERROR_LENGTH);
    }
}

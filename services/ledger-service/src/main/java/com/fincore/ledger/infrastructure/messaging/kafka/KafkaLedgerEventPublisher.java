package com.fincore.ledger.infrastructure.messaging.kafka;

import java.util.concurrent.TimeUnit;

import com.fincore.ledger.application.port.LedgerEventPublisher;
import com.fincore.ledger.application.query.LedgerOutboxMessage;
import com.fincore.ledger.config.LedgerKafkaProperties;
import com.fincore.ledger.config.LedgerOutboxRelayProperties;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaLedgerEventPublisher implements LedgerEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final LedgerKafkaProperties kafkaProperties;
    private final LedgerOutboxRelayProperties relayProperties;

    @Override
    public void publish(LedgerOutboxMessage message) {
        try {
            kafkaTemplate.send(kafkaProperties.ledgerTopic(), message.aggregateId().toString(), message.payload())
                    .get(relayProperties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to publish ledger outbox event", exception);
        }
    }
}

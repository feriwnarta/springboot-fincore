package com.fincore.loan.infrastructure.messaging.kafka;

import java.util.concurrent.TimeUnit;

import com.fincore.loan.application.port.LoanEventPublisher;
import com.fincore.loan.application.query.LoanOutboxMessage;
import com.fincore.loan.config.LoanKafkaProperties;
import com.fincore.loan.config.LoanOutboxRelayProperties;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaLoanEventPublisher implements LoanEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final LoanKafkaProperties kafkaProperties;
    private final LoanOutboxRelayProperties relayProperties;

    @Override
    public void publish(LoanOutboxMessage message) throws Exception {
        kafkaTemplate.send(
                        kafkaProperties.loanTopic(),
                        message.aggregateId().toString(),
                        message.payloadJson())
                .get(relayProperties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
    }
}

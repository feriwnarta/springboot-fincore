package com.fincore.ledger.config;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.DefaultErrorHandler;

class KafkaConsumerConfigurationTest {

    @Test
    void createsBoundedRetryErrorHandlerWithDlqRecoverer() {
        var configuration = new KafkaConsumerConfiguration();
        var handler = configuration.kafkaErrorHandler(
                new org.springframework.kafka.core.KafkaTemplate<>(
                        new org.springframework.kafka.core.DefaultKafkaProducerFactory<>(java.util.Map.of())),
                Duration.ofSeconds(1),
                3L,
                ".DLQ");

        assertThat(handler).isInstanceOf(DefaultErrorHandler.class);
    }
}

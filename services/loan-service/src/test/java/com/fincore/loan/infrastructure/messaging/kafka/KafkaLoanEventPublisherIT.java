package com.fincore.loan.infrastructure.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import com.fincore.loan.LoanServiceApplication;
import com.fincore.loan.application.port.LoanEventPublisher;
import com.fincore.loan.application.query.LoanOutboxMessage;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

@Testcontainers
@SpringBootTest(classes = LoanServiceApplication.class)
@ActiveProfiles("test")
class KafkaLoanEventPublisherIT {

    private static final String TOPIC = "fincore.loan.events.v1";

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.0");

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("fincore.kafka.loan-topic", () -> TOPIC);
    }

    @Autowired
    private LoanEventPublisher publisher;

    @BeforeAll
    static void createTopic() throws Exception {
        try (AdminClient adminClient = AdminClient.create(Maps.of(
                "bootstrap.servers", KAFKA.getBootstrapServers()))) {
            adminClient.createTopics(List.of(new NewTopic(TOPIC, 1, (short) 1))).all().get();
        }
    }

    @Test
    void publishesEventUsingAggregateIdAsKey() throws Exception {
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID aggregateId = UUID.fromString("10000000-0000-0000-0000-000000000011");
        LoanOutboxMessage message = new LoanOutboxMessage(
                eventId,
                aggregateId,
                "LoanSubmitted",
                "APP-11",
                "{\"eventType\":\"LoanSubmitted\"}",
                UUID.fromString("20000000-0000-0000-0000-000000000011"),
                java.time.Instant.parse("2026-01-01T00:00:00Z"));

        publisher.publish(message);

        Properties consumerProperties = new Properties();
        consumerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        consumerProperties.put(ConsumerConfig.GROUP_ID_CONFIG, "loan-publisher-it-" + UUID.randomUUID());
        consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProperties)) {
            consumer.subscribe(List.of(TOPIC));
            ConsumerRecord<String, String> record = null;
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (record == null && System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> candidate : consumer.poll(Duration.ofMillis(250))) {
                    if (aggregateId.toString().equals(candidate.key())) {
                        record = candidate;
                        break;
                    }
                }
            }

            assertThat(record).isNotNull();
            assertThat(record.key()).isEqualTo(aggregateId.toString());
            assertThat(record.value()).isEqualTo(message.payloadJson());
        }
    }

    private static final class Maps {
        private Maps() {
        }

        private static Properties of(String key, String value) {
            Properties properties = new Properties();
            properties.put(key, value);
            return properties;
        }
    }
}

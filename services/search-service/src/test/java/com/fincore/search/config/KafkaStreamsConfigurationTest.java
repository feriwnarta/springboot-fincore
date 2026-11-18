package com.fincore.search.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.StreamsBuilder;
import org.junit.jupiter.api.Test;

class KafkaStreamsConfigurationTest {

    @Test
    void duplicateLoanFundedEventIsCountedOnce() {
        StreamsBuilder builder = new StreamsBuilder();
        new KafkaStreamsConfiguration().loanAnalyticsStream(builder, "loan.events", "loan.analytics");
        Topology topology = builder.build();
        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "search-test");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");
        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        properties.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

        try (TopologyTestDriver driver = new TopologyTestDriver(topology, properties)) {
            TestInputTopic<String, String> input = driver.createInputTopic(
                    "loan.events", Serdes.String().serializer(), Serdes.String().serializer());
            TestOutputTopic<String, String> output = driver.createOutputTopic(
                    "loan.analytics", Serdes.String().deserializer(), Serdes.String().deserializer());
            String event = "{\"eventId\":\"evt-1\",\"eventType\":\"LoanFunded\","
                    + "\"payload\":{\"productCode\":\"P-001\"}}";

            input.pipeInput("loan-1", event, 1_000L);
            input.pipeInput("loan-1", event, 2_000L);

            assertThat(output.readValuesToList()).hasSize(1)
                    .first().asString().contains("\"count\":1");
        }
    }
}

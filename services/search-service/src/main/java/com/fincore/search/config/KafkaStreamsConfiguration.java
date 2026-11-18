package com.fincore.search.config;

import java.time.Duration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.ProcessorSupplier;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;

@Configuration
@EnableKafkaStreams
public class KafkaStreamsConfiguration {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEDUPLICATION_STORE = "loan-event-deduplication-v1";

    @Bean
    KStream<String, String> loanAnalyticsStream(
            StreamsBuilder builder,
            @Value("${fincore.kafka.loan-topic:fincore.loan.events.v1}") String loanTopic,
            @Value("${fincore.kafka.analytics-topic:fincore.loan.analytics.5m.v1}") String analyticsTopic) {
        StoreBuilder<KeyValueStore<String, Long>> deduplicationStore = Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore(DEDUPLICATION_STORE), Serdes.String(), Serdes.Long());
        builder.addStateStore(deduplicationStore);
        KStream<String, String> source = builder.stream(loanTopic, Consumed.with(Serdes.String(), Serdes.String()))
                .process((ProcessorSupplier<String, String, String, String>) EventDeduplicator::new, DEDUPLICATION_STORE);
        source
                .filter((key, value) -> isLoanFunded(value))
                .selectKey((key, value) -> productCode(value))
                .groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
                .windowedBy(TimeWindows.ofSizeAndGrace(Duration.ofMinutes(5), Duration.ofMinutes(1)))
                .count()
                .toStream()
                .map((window, count) -> KeyValue.pair(window.key(), "{\"count\":" + count
                        + ",\"windowStart\":\"" + window.window().startTime() + "\"}"))
                .to(analyticsTopic, Produced.with(Serdes.String(), Serdes.String()));
        return source;
    }

    private static boolean isLoanFunded(String value) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(value);
            return "LoanFunded".equals(root.path("eventType").asText());
        } catch (Exception exception) {
            return false;
        }
    }

    private static String productCode(String value) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(value);
            JsonNode payload = root.has("payload") ? root.get("payload") : root;
            return payload.path("productCode").asText("UNKNOWN");
        } catch (Exception exception) {
            return "UNKNOWN";
        }
    }

    private static final class EventDeduplicator implements Processor<String, String, String, String> {

        private static final long RETENTION_MILLIS = Duration.ofHours(1).toMillis();
        private KeyValueStore<String, Long> store;
        private ProcessorContext<String, String> context;

        @Override
        public void init(ProcessorContext<String, String> context) {
            this.context = context;
            this.store = (KeyValueStore<String, Long>) context.getStateStore(DEDUPLICATION_STORE);
        }

        @Override
        public void process(Record<String, String> record) {
            long timestamp = record.timestamp() < 0 ? System.currentTimeMillis() : record.timestamp();
            purge(timestamp);
            String eventId = eventId(record.value());
            Long seenAt = store.get(eventId);
            if (seenAt != null && timestamp - seenAt <= RETENTION_MILLIS) {
                return;
            }
            store.put(eventId, timestamp);
            context.forward(record);
        }

        private void purge(long timestamp) {
            try (var iterator = store.all()) {
                while (iterator.hasNext()) {
                    var entry = iterator.next();
                    if (timestamp - entry.value > RETENTION_MILLIS) {
                        store.delete(entry.key);
                    }
                }
            }
        }

        private static String eventId(String value) {
            try {
                String eventId = OBJECT_MAPPER.readTree(value).path("eventId").asText();
                return eventId.isBlank() ? Integer.toHexString(value.hashCode()) : eventId;
            } catch (Exception exception) {
                return Integer.toHexString(value.hashCode());
            }
        }
    }
}

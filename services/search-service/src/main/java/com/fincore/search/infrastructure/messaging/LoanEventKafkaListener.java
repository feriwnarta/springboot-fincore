package com.fincore.search.infrastructure.messaging;

import java.time.Instant;

import com.fincore.search.application.service.LoanProjectionService;
import com.fincore.search.application.service.SearchCatalogProjectionService;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoanEventKafkaListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoanEventKafkaListener.class);

    private final LoanEventProjectionParser parser;
    private final LoanProjectionService projectionService;
    private final CatalogEventProjectionParser catalogParser;
    private final SearchCatalogProjectionService catalogProjectionService;

    @KafkaListener(
            topics = {
                    "${fincore.kafka.loan-topic:fincore.loan.events.v1}",
                    "${fincore.kafka.ledger-topic:fincore.ledger.events.v1}"
            },
            groupId = "${fincore.kafka.consumer-group:fincore-search-service}",
            autoStartup = "${fincore.kafka.consumer-auto-startup:false}")
    public void onMessage(ConsumerRecord<String, String> record) {
        Instant recordTimestamp = record.timestamp() == ConsumerRecord.NO_TIMESTAMP
                ? Instant.EPOCH
                : Instant.ofEpochMilli(record.timestamp());
        var loanEvent = parser.parse(record.value(), recordTimestamp);
        if (loanEvent.isPresent()) {
            var event = loanEvent.get();
            MDC.put("eventId", event.eventId().toString());
            MDC.put("eventType", event.eventType());
            MDC.put("aggregateId", event.loanId().toString());
            try {
                projectionService.project(event);
                LOGGER.info("loan_projection_updated eventId={} loanId={} status={}",
                        event.eventId(), event.loanId(), event.status());
            } finally {
                MDC.remove("eventId");
                MDC.remove("eventType");
                MDC.remove("aggregateId");
            }
            return;
        }
        catalogParser.parse(record.value(), recordTimestamp).ifPresentOrElse(event -> {
            MDC.put("eventId", event.eventId().toString());
            MDC.put("eventType", event.eventType());
            MDC.put("aggregateId", event.aggregateId().toString());
            try {
                catalogProjectionService.project(event);
                LOGGER.info("catalog_projection_updated eventId={} aggregateId={} eventType={}",
                        event.eventId(), event.aggregateId(), event.eventType());
            } finally {
                MDC.remove("eventId");
                MDC.remove("eventType");
                MDC.remove("aggregateId");
            }
        }, () -> LOGGER.debug("search_event_ignored eventType=not-supported"));
    }
}

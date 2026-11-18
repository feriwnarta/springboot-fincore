package com.fincore.loan.infrastructure.messaging.kafka;

import com.fincore.loan.application.service.FundingJournalPostedEventHandler;
import com.fincore.loan.application.service.RepaymentJournalPostedEventHandler;
import com.fincore.loan.domain.model.FundingJournalPostedEvent;
import com.fincore.loan.domain.model.RepaymentJournalPostedEvent;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FundingJournalPostedKafkaListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(FundingJournalPostedKafkaListener.class);

    private final FundingJournalPostedEventParser parser;
    private final FundingJournalPostedEventHandler handler;
    private final RepaymentJournalPostedEventParser repaymentParser;
    private final RepaymentJournalPostedEventHandler repaymentHandler;

    @KafkaListener(
            topics = "${fincore.kafka.ledger-topic:fincore.ledger.events.v1}",
            groupId = "${fincore.kafka.ledger-consumer-group:fincore-loan-service}",
            autoStartup = "${fincore.kafka.ledger-consumer-auto-startup:false}")
    public void onMessage(String payload) {
        parser.parse(payload).ifPresentOrElse(this::handle, () ->
                repaymentParser.parse(payload).ifPresentOrElse(
                        this::handleRepaymentJournalPosted,
                        () -> LOGGER.debug("ledger_event_ignored eventType=not-supported")));
    }

    private void handleRepaymentJournalPosted(RepaymentJournalPostedEvent event) {
        MDC.put("correlationId", event.correlationId().toString());
        MDC.put("eventId", event.eventId().toString());
        MDC.put("eventType", "RepaymentJournalPosted");
        MDC.put("aggregateId", event.aggregateId().toString());
        MDC.put("businessReference", event.businessReference());
        try {
            LOGGER.info("repayment_journal_posted_event_received eventId={} loanId={} repaymentId={}",
                    event.eventId(), event.aggregateId(), event.repaymentId());
            LOGGER.info("repayment_journal_posted_event_handled eventId={} outcome={}",
                    event.eventId(), repaymentHandler.handle(event));
        } finally {
            MDC.remove("correlationId");
            MDC.remove("eventId");
            MDC.remove("eventType");
            MDC.remove("aggregateId");
            MDC.remove("businessReference");
        }
    }

    private void handle(FundingJournalPostedEvent event) {
        MDC.put("correlationId", event.correlationId().toString());
        MDC.put("eventId", event.eventId().toString());
        MDC.put("eventType", "FundingJournalPosted");
        MDC.put("aggregateId", event.aggregateId().toString());
        MDC.put("businessReference", event.businessReference());
        try {
            LOGGER.info("funding_journal_posted_event_received eventId={} loanId={} journalId={}",
                    event.eventId(), event.aggregateId(), event.journalId());
            LOGGER.info("funding_journal_posted_event_handled eventId={} outcome={}",
                    event.eventId(), handler.handle(event));
        } finally {
            MDC.remove("correlationId");
            MDC.remove("eventId");
            MDC.remove("eventType");
            MDC.remove("aggregateId");
            MDC.remove("businessReference");
        }
    }
}

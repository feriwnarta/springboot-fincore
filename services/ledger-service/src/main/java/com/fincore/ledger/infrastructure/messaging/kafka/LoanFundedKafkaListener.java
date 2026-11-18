package com.fincore.ledger.infrastructure.messaging.kafka;

import com.fincore.ledger.application.service.LoanFundedEventHandler;
import com.fincore.ledger.application.service.RepaymentConfirmedEventHandler;
import com.fincore.ledger.domain.model.LoanFundedEvent;
import com.fincore.ledger.domain.model.RepaymentConfirmedEvent;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoanFundedKafkaListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoanFundedKafkaListener.class);

    private final LoanFundedEventParser parser;
    private final LoanFundedEventHandler handler;
    private final RepaymentConfirmedEventParser repaymentParser;
    private final RepaymentConfirmedEventHandler repaymentHandler;

    @KafkaListener(
            topics = "${fincore.kafka.loan-topic:fincore.loan.events.v1}",
            groupId = "${fincore.kafka.consumer-group:fincore-ledger-service}")
    public void onMessage(String payload) {
        var event = parser.parse(payload);
        event.ifPresentOrElse(
                this::handleLoanFundedEvent,
                () -> repaymentParser.parse(payload).ifPresentOrElse(
                        this::handleRepaymentConfirmedEvent,
                        () -> LOGGER.debug("loan_event_ignored eventType=not-supported")));
    }

    private void handleRepaymentConfirmedEvent(RepaymentConfirmedEvent event) {
        putMessageContext(event);
        try {
            LOGGER.info("repayment_confirmed_event_received eventId={} aggregateId={} businessReference={}",
                    event.eventId(), event.aggregateId(), event.businessReference());
            LOGGER.info("repayment_confirmed_event_handled eventId={} businessReference={} outcome={}",
                    event.eventId(), event.businessReference(), repaymentHandler.handle(event));
        } finally {
            clearMessageContext();
        }
    }

    private void handleLoanFundedEvent(LoanFundedEvent event) {
        putMessageContext(event);
        try {
            LOGGER.info("loan_funded_event_received eventId={} aggregateId={} businessReference={}",
                    event.eventId(), event.aggregateId(), event.businessReference());
            var result = handler.handle(event);
            LOGGER.info("loan_funded_event_handled eventId={} businessReference={} outcome={}",
                    event.eventId(), event.businessReference(), result);
        } finally {
            clearMessageContext();
        }
    }

    private static void putMessageContext(LoanFundedEvent event) {
        MDC.put("correlationId", event.correlationId().toString());
        MDC.put("eventId", event.eventId().toString());
        MDC.put("eventType", "LoanFunded");
        MDC.put("aggregateId", event.aggregateId().toString());
        MDC.put("businessReference", event.businessReference());
    }

    private static void putMessageContext(RepaymentConfirmedEvent event) {
        MDC.put("correlationId", event.correlationId().toString());
        MDC.put("eventId", event.eventId().toString());
        MDC.put("eventType", "RepaymentConfirmed");
        MDC.put("aggregateId", event.aggregateId().toString());
        MDC.put("businessReference", event.businessReference());
    }

    private static void clearMessageContext() {
        MDC.remove("correlationId");
        MDC.remove("eventId");
        MDC.remove("eventType");
        MDC.remove("aggregateId");
        MDC.remove("businessReference");
    }
}

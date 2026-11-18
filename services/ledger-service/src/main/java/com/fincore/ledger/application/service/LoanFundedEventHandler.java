package com.fincore.ledger.application.service;

import com.fincore.ledger.application.command.PostJournalCommand;
import com.fincore.ledger.domain.model.LoanFundedEvent;
import com.fincore.ledger.domain.port.ProcessedEventRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoanFundedEventHandler {

    private static final String EVENT_TYPE = "LoanFunded";
    private static final String DEBIT_ACCOUNT = "LOAN_RECEIVABLE";
    private static final String CREDIT_ACCOUNT = "SIMULATED_SETTLEMENT_CLEARING";

    private final ProcessedEventRepository processedEventRepository;
    private final JournalPostingService journalPostingService;

    @Transactional
    public EventHandlingResult handle(LoanFundedEvent event) {
        if (!processedEventRepository.claim(event.eventId(), EVENT_TYPE)) {
            return EventHandlingResult.DUPLICATE;
        }
        journalPostingService.post(new PostJournalCommand(
                event.businessReference(),
                event.currency(),
                DEBIT_ACCOUNT,
                CREDIT_ACCOUNT,
                event.principalAmount(),
                "loan-service",
                "event-consumer",
                event.correlationId().toString(),
                event.eventId().toString(),
                event.aggregateId()));
        return EventHandlingResult.PROCESSED;
    }
}

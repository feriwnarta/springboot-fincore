package com.fincore.ledger.application.service;

import com.fincore.ledger.domain.model.RepaymentConfirmedEvent;
import com.fincore.ledger.domain.port.ProcessedEventRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RepaymentConfirmedEventHandler {

    private static final String EVENT_TYPE = "RepaymentConfirmed";

    private final ProcessedEventRepository processedEventRepository;
    private final RepaymentJournalPostingService journalPostingService;

    @Transactional
    public EventHandlingResult handle(RepaymentConfirmedEvent event) {
        if (!processedEventRepository.claim(event.eventId(), EVENT_TYPE)) {
            return EventHandlingResult.DUPLICATE;
        }
        journalPostingService.post(event);
        return EventHandlingResult.PROCESSED;
    }
}

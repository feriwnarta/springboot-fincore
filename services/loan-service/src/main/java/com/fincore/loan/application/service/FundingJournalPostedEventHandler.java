package com.fincore.loan.application.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fincore.loan.domain.model.FundingJournalPostedEvent;
import com.fincore.loan.domain.model.LoanAuditEntry;
import com.fincore.loan.domain.port.LoanAuditRepository;
import com.fincore.loan.domain.port.LoanFundingRepository;
import com.fincore.loan.domain.port.LoanProcessedEventRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FundingJournalPostedEventHandler {

    private static final String EVENT_TYPE = "FundingJournalPosted";

    private final LoanProcessedEventRepository processedEventRepository;
    private final LoanFundingRepository fundingRepository;
    private final LoanAuditRepository auditRepository;
    private final RepaymentScheduleService repaymentScheduleService;

    @Transactional
    public LoanEventHandlingResult handle(FundingJournalPostedEvent event) {
        if (!processedEventRepository.claim(event.eventId(), EVENT_TYPE)) {
            return LoanEventHandlingResult.DUPLICATE;
        }
        if (!fundingRepository.markLedgerPosted(event.aggregateId(), Instant.now())) {
            throw new IllegalStateException("Funding journal acknowledgement cannot update the loan projection");
        }
        repaymentScheduleService.generateForFundedLoan(event.aggregateId(), event.occurredAt());
        auditRepository.append(new LoanAuditEntry(
                UUID.randomUUID(), event.eventId(), "Loan", event.aggregateId().toString(),
                event.aggregateVersion(), EVENT_TYPE, "SUCCESS", "ledger-service", "event-consumer",
                event.correlationId().toString(), event.eventId().toString(), event.businessReference(), null,
                LoanLedgerEventJson.serializeObject(Map.of(
                        "source", "ledger-service",
                        "journalId", event.journalId().toString(),
                        "amount", event.amount().toPlainString(),
                        "currency", event.currency())), Instant.now()));
        return LoanEventHandlingResult.PROCESSED;
    }
}

package com.fincore.loan.application.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.fincore.loan.domain.model.LoanAuditEntry;
import com.fincore.loan.domain.model.RepaymentAllocation;
import com.fincore.loan.domain.model.RepaymentJournalPostedEvent;
import com.fincore.loan.domain.port.LoanAuditRepository;
import com.fincore.loan.domain.port.LoanFundingRepository;
import com.fincore.loan.domain.port.LoanProcessedEventRepository;
import com.fincore.loan.domain.port.LoanRepaymentRepository;
import com.fincore.loan.domain.port.LoanRepaymentScheduleRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RepaymentJournalPostedEventHandler {

    private static final String EVENT_TYPE = "RepaymentJournalPosted";
    private static final String PAID_OFF_ACTION = "LOAN_PAID_OFF";
    private static final String CURRENCY = "IDR";

    private final LoanProcessedEventRepository processedEventRepository;
    private final LoanFundingRepository fundingRepository;
    private final LoanRepaymentRepository repaymentRepository;
    private final LoanRepaymentScheduleRepository scheduleRepository;
    private final LoanAuditRepository auditRepository;

    @Transactional
    public LoanEventHandlingResult handle(RepaymentJournalPostedEvent event) {
        var repayment = repaymentRepository.findById(event.repaymentId())
                .orElseThrow(() -> new IllegalStateException("Repayment acknowledgement has no local repayment"));
        validateAcknowledgement(event, repayment);
        if (!processedEventRepository.claim(event.eventId(), EVENT_TYPE)) {
            return LoanEventHandlingResult.DUPLICATE;
        }
        for (RepaymentAllocation allocation : repaymentRepository.findAllocations(repayment.id())) {
            if (!scheduleRepository.applyAllocation(
                    allocation.scheduleId(), allocation.scheduleVersion(),
                    allocation.principalAmount(), allocation.interestAmount())) {
                throw new IllegalStateException("Repayment schedule changed before ledger acknowledgement");
            }
        }
        if (scheduleRepository.allPaid(repayment.loanId())
                && fundingRepository.markPaidOff(repayment.loanId(), Instant.now())) {
            UUID payoffEventId = UUID.nameUUIDFromBytes(
                    (PAID_OFF_ACTION + ":" + event.eventId()).getBytes(StandardCharsets.UTF_8));
            auditRepository.append(new LoanAuditEntry(
                    UUID.randomUUID(), payoffEventId, "Loan", repayment.loanId().toString(),
                    event.aggregateVersion(), PAID_OFF_ACTION, "SUCCESS", "loan-service", "event-consumer",
                    event.correlationId().toString(), event.eventId().toString(), event.businessReference(), null,
                    LoanLedgerEventJson.serializeObject(Map.of(
                            "source", "loan-service",
                            "reason", "all_repayment_schedules_paid")), Instant.now()));
        }
        if (!repaymentRepository.markPosted(repayment.id(), event.journalId().toString(), Instant.now())) {
            throw new IllegalStateException("Repayment acknowledgement cannot update local repayment");
        }
        auditRepository.append(new LoanAuditEntry(
                UUID.randomUUID(), event.eventId(), "Repayment", repayment.id().toString(),
                event.aggregateVersion(), EVENT_TYPE, "SUCCESS", "ledger-service", "event-consumer",
                event.correlationId().toString(), event.eventId().toString(), event.businessReference(), null,
                LoanLedgerEventJson.serializeObject(Map.of(
                        "source", "ledger-service",
                        "journalId", event.journalId().toString(),
                        "amount", event.amount().toPlainString(),
                        "currency", event.currency())), Instant.now()));
        return LoanEventHandlingResult.PROCESSED;
    }

    private static void validateAcknowledgement(RepaymentJournalPostedEvent event, com.fincore.loan.domain.model.Repayment repayment) {
        if (!event.repaymentId().equals(repayment.id())
                || !event.aggregateId().equals(repayment.loanId())
                || event.amount() == null
                || event.amount().compareTo(repayment.amount()) != 0
                || !CURRENCY.equals(event.currency())
                || !Objects.equals(event.businessReference(), repayment.businessReference())) {
            throw new IllegalArgumentException("Repayment journal acknowledgement does not match the local repayment");
        }
    }
}

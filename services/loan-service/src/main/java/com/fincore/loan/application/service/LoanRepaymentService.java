package com.fincore.loan.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fincore.loan.application.command.RequestRepaymentCommand;
import com.fincore.loan.domain.enums.IdempotencyStatus;
import com.fincore.loan.domain.enums.LedgerPostingStatus;
import com.fincore.loan.domain.enums.LoanFundingStatus;
import com.fincore.loan.domain.enums.RepaymentStatus;
import com.fincore.loan.domain.model.IdempotencyRecord;
import com.fincore.loan.domain.model.LoanAuditEntry;
import com.fincore.loan.domain.model.LoanFunding;
import com.fincore.loan.domain.model.LoanOutboxEntry;
import com.fincore.loan.domain.model.Repayment;
import com.fincore.loan.domain.model.RepaymentAllocation;
import com.fincore.loan.domain.model.RepaymentSchedule;
import com.fincore.loan.domain.port.LoanAuditRepository;
import com.fincore.loan.domain.port.LoanFundingRepository;
import com.fincore.loan.domain.port.LoanIdempotencyRepository;
import com.fincore.loan.domain.port.LoanOutboxRepository;
import com.fincore.loan.domain.port.LoanRepaymentRepository;
import com.fincore.loan.domain.port.LoanRepaymentScheduleRepository;
import com.fincore.platform.event.CorrelationIds;
import com.fincore.platform.security.AuditActor;
import com.fincore.platform.security.CurrentIdentity;
import com.fincore.platform.web.ConflictException;
import com.fincore.platform.web.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoanRepaymentService {

    private static final String OPERATION = "REQUEST_REPAYMENT";
    private static final String CURRENCY = "IDR";
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4);

    private final LoanFundingRepository fundingRepository;
    private final LoanRepaymentRepository repaymentRepository;
    private final LoanRepaymentScheduleRepository scheduleRepository;
    private final LoanAuditRepository auditRepository;
    private final LoanIdempotencyRepository idempotencyRepository;
    private final LoanOutboxRepository outboxRepository;
    private final CurrentIdentity currentIdentity;

    @Transactional
    public Repayment request(RequestRepaymentCommand command) {
        AuditActor actor = AuditActor.from(currentIdentity, "LOAN_OPERATOR", "LOAN_APPROVER");
        LoanFunding funding = fundingRepository.findById(command.loanId())
                .orElseThrow(() -> new ResourceNotFoundException("Loan was not found"));
        currentIdentity.requireCustomerAccess(funding.customerId());
        IdempotencyRecord requested = new IdempotencyRecord(
                command.loanId().toString(), OPERATION, command.idempotencyKey(), money(command.amount()).toPlainString(),
                null, IdempotencyStatus.IN_PROGRESS);
        var existing = findIdempotencyRecord(requested);
        if (existing.isPresent()) {
            return replay(command, requested, existing.get());
        }
        if (funding.status() != LoanFundingStatus.FUNDED
                || funding.ledgerPostingStatus() != LedgerPostingStatus.POSTED) {
            throw new ConflictException("Loan is not ready for repayment");
        }

        UUID correlationId = CorrelationIds.resolve(command.correlationId());
        if (!idempotencyRepository.createIfAbsent(requested)) {
            return replay(command, requested, findIdempotencyRecord(requested)
                    .orElseThrow(() -> new ConflictException("Idempotency request is being processed")));
        }

        List<RepaymentSchedule> schedules = scheduleRepository.findOpenForUpdate(command.loanId());
        List<RepaymentAllocation> allocations = allocate(command.amount(), schedules);
        UUID repaymentId = UUID.randomUUID();
        Instant now = Instant.now();
        Repayment repayment = new Repayment(
                repaymentId,
                command.loanId(),
                "REPAY-" + repaymentId,
                money(command.amount()),
                RepaymentStatus.CONFIRMED_AWAITING_LEDGER,
                "RCPT-" + repaymentId,
                null,
                command.idempotencyKey(),
                now,
                null);
        try {
            repaymentRepository.save(repayment, allocations);
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("Another repayment is already in flight for this loan");
        }

        BigDecimal principal = allocations.stream()
                .map(RepaymentAllocation::principalAmount).reduce(ZERO, BigDecimal::add);
        BigDecimal interest = allocations.stream()
                .map(RepaymentAllocation::interestAmount).reduce(ZERO, BigDecimal::add);
        UUID eventId = UUID.randomUUID();
        auditRepository.append(new LoanAuditEntry(
                UUID.randomUUID(), eventId, "Repayment", repayment.id().toString(), 1,
                "REPAYMENT_CONFIRMED", "SUCCESS", actor.id(), actor.role(),
                correlationId.toString(), command.requestId(), repayment.businessReference(), null,
                LoanLedgerEventJson.serializeObject(Map.of(
                        "source", "loan-service",
                        "currency", CURRENCY,
                        "principal", principal.toPlainString(),
                        "interest", interest.toPlainString())), now));
        outboxRepository.append(new LoanOutboxEntry(
                eventId, repayment.loanId(), 1, "RepaymentConfirmed", 1,
                repayment.businessReference(), repaymentEventPayload(
                        repayment, eventId, correlationId, now, principal, interest), now));
        idempotencyRepository.markSucceeded(requested, repayment.id().toString());
        return repayment;
    }

    private Optional<IdempotencyRecord> findIdempotencyRecord(IdempotencyRecord requested) {
        return idempotencyRepository.findByKey(requested.callerScope(), requested.operation(), requested.idemKey());
    }

    private Repayment replay(RequestRepaymentCommand command, IdempotencyRecord requested, IdempotencyRecord existing) {
        if (!existing.requestFingerprint().equals(requested.requestFingerprint())) {
            throw new ConflictException("Idempotency key was already used with a different request");
        }
        if (existing.status() != IdempotencyStatus.SUCCEEDED || existing.resultRef() == null) {
            throw new ConflictException("Idempotency request is being processed");
        }
        UUID repaymentId;
        try {
            repaymentId = UUID.fromString(existing.resultRef());
        } catch (IllegalArgumentException exception) {
            throw new ConflictException("Idempotency result is unavailable");
        }
        return repaymentRepository.findById(repaymentId)
                .filter(repayment -> repaymentId.equals(repayment.id()))
                .filter(repayment -> command.loanId().equals(repayment.loanId()))
                .filter(repayment -> command.idempotencyKey().equals(repayment.idempotencyKey()))
                .orElseThrow(() -> new ConflictException("Idempotency result is unavailable"));
    }

    private static List<RepaymentAllocation> allocate(BigDecimal requestedAmount, List<RepaymentSchedule> schedules) {
        if (schedules.isEmpty()) {
            throw new ConflictException("Loan has no outstanding repayment schedule");
        }
        BigDecimal remaining = money(requestedAmount);
        List<RepaymentAllocation> allocations = new ArrayList<>();
        for (RepaymentSchedule schedule : schedules) {
            BigDecimal interest = remaining.min(money(schedule.outstandingInterest()));
            remaining = money(remaining.subtract(interest));
            BigDecimal principal = remaining.min(money(schedule.outstandingPrincipal()));
            remaining = money(remaining.subtract(principal));
            if (interest.signum() > 0 || principal.signum() > 0) {
                allocations.add(new RepaymentAllocation(schedule.id(), principal, interest, schedule.version()));
            }
            if (remaining.signum() == 0) {
                break;
            }
        }
        if (remaining.signum() > 0) {
            throw new IllegalArgumentException("Payment amount exceeds outstanding balance");
        }
        return allocations;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private static String repaymentEventPayload(
            Repayment repayment, UUID eventId, UUID correlationId, Instant occurredAt,
            BigDecimal principal, BigDecimal interest) {
        return LoanLedgerEventJson.serialize(eventId, "RepaymentConfirmed", repayment.loanId(), 1,
                repayment.businessReference(), occurredAt, correlationId, Map.of(
                        "repaymentId", repayment.id().toString(),
                        "currency", CURRENCY,
                        "amount", repayment.amount().toPlainString(),
                        "principalAmount", principal.toPlainString(),
                        "interestAmount", interest.toPlainString()));
    }
}

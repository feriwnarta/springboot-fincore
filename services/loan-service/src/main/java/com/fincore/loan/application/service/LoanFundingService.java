package com.fincore.loan.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import com.fincore.loan.application.command.RequestFundingCommand;
import com.fincore.loan.domain.enums.IdempotencyStatus;
import com.fincore.loan.domain.enums.LedgerPostingStatus;
import com.fincore.loan.domain.enums.LoanApplicationStatus;
import com.fincore.loan.domain.enums.LoanFundingStatus;
import com.fincore.loan.domain.model.IdempotencyRecord;
import com.fincore.loan.domain.model.LoanApplication;
import com.fincore.loan.domain.model.LoanAuditEntry;
import com.fincore.loan.domain.model.LoanFunding;
import com.fincore.loan.domain.model.LoanOutboxEntry;
import com.fincore.loan.domain.port.LoanApplicationRepository;
import com.fincore.loan.domain.port.LoanAuditRepository;
import com.fincore.loan.domain.port.LoanFundingRepository;
import com.fincore.loan.domain.port.LoanIdempotencyRepository;
import com.fincore.loan.domain.port.LoanOutboxRepository;
import com.fincore.platform.event.CorrelationIds;
import com.fincore.platform.security.AuditActor;
import com.fincore.platform.security.CurrentIdentity;
import com.fincore.platform.web.ConflictException;
import com.fincore.platform.web.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoanFundingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoanFundingService.class);
    private static final String OPERATION = "REQUEST_LOAN_FUNDING";
    private static final String CURRENCY = "IDR";

    private final LoanApplicationRepository applicationRepository;
    private final LoanFundingRepository fundingRepository;
    private final LoanAuditRepository auditRepository;
    private final LoanIdempotencyRepository idempotencyRepository;
    private final LoanOutboxRepository outboxRepository;
    private final FundingSimulator fundingSimulator;
    private final CurrentIdentity currentIdentity;

    @Transactional
    public LoanFunding requestFunding(RequestFundingCommand command) {
        AuditActor actor = AuditActor.from(currentIdentity, "LOAN_OPERATOR", "LOAN_APPROVER");
        LoanApplication application = applicationRepository.findById(command.applicationId())
                .orElseThrow(() -> new ResourceNotFoundException("Loan application was not found"));
        currentIdentity.requireCustomerAccess(application.customerId());
        UUID correlationId = CorrelationIds.resolve(command.correlationId());
        IdempotencyRecord requested = new IdempotencyRecord(
                command.applicationId().toString(), OPERATION, command.idempotencyKey(), fingerprintFor(command),
                null, IdempotencyStatus.IN_PROGRESS);
        if (!idempotencyRepository.createIfAbsent(requested)) {
            return replay(requested, application.customerId());
        }
        if (application.status() != LoanApplicationStatus.APPROVED) {
            throw new ConflictException("Only approved loan applications can be funded");
        }
        if (fundingRepository.findByApplicationId(application.id()).isPresent()) {
            throw new ConflictException("Loan funding has already been requested");
        }

        Instant now = Instant.now();
        LoanFunding pending = new LoanFunding(
                application.id(), application.id(), application.customerId(), application.productCode(),
                application.principalAmount(), LoanFundingStatus.FUNDING_PENDING, LedgerPostingStatus.PENDING,
                1, null, now, now);
        fundingRepository.save(pending);
        FundingSimulationResult simulation = fundingSimulator.simulate(application, command);
        if (!simulation.successful()) {
            if (!fundingRepository.markFailed(pending.id(), pending.version(), now)) {
                throw new ConflictException("Loan funding state changed while handling simulator result");
            }
            auditRepository.append(audit(pending, actor, correlationId, command.requestId(),
                    "FUNDING_FAILED", simulation.reason(), now, UUID.randomUUID()));
            idempotencyRepository.markSucceeded(requested, pending.id().toString());
            return new LoanFunding(
                    pending.id(), pending.applicationId(), pending.customerId(), pending.productCode(),
                    pending.principalAmount(), LoanFundingStatus.FUNDING_FAILED, LedgerPostingStatus.PENDING,
                    2, null, pending.createdAt(), now);
        }

        UUID eventId = UUID.randomUUID();
        if (!fundingRepository.markFunded(pending.id(), pending.version(), now, now)) {
            throw new ConflictException("Loan funding state changed while confirming simulator result");
        }
        LoanFunding funded = new LoanFunding(
                pending.id(), pending.applicationId(), pending.customerId(), pending.productCode(),
                pending.principalAmount(), LoanFundingStatus.FUNDED, LedgerPostingStatus.PENDING,
                2, now, pending.createdAt(), now);
        auditRepository.append(audit(funded, actor, correlationId, command.requestId(),
                "FUNDED", simulation.reason(), now, eventId));
        outboxRepository.append(new LoanOutboxEntry(
                eventId, funded.id(), funded.version(), "LoanFunded", 1, businessReference(funded),
                fundedEventPayload(funded, eventId, correlationId, now), now));
        idempotencyRepository.markSucceeded(requested, funded.id().toString());
        LOGGER.info("loan_funding_succeeded loanId={} eventId={} businessReference={}",
                funded.id(), eventId, businessReference(funded));
        return funded;
    }

    public static String fingerprintFor(RequestFundingCommand command) {
        String canonical = command.applicationId() + "|" + command.simulateFailure();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private LoanFunding replay(IdempotencyRecord requested, String customerId) {
        IdempotencyRecord existing = idempotencyRepository.findByKey(
                        requested.callerScope(), requested.operation(), requested.idemKey())
                .orElseThrow(() -> new ConflictException("Idempotency request is being processed"));
        UUID resultId = IdempotencyReplay.resultId(requested, existing);
        LoanFunding funding = fundingRepository.findById(resultId)
                .filter(result -> resultId.equals(result.id())
                        && requested.callerScope().equals(result.applicationId().toString()))
                .orElseThrow(() -> new ConflictException("Idempotency result is unavailable"));
        if (!customerId.equals(funding.customerId())) {
            throw new AccessDeniedException("Customer access denied");
        }
        return funding;
    }

    private static LoanAuditEntry audit(
            LoanFunding funding, AuditActor actor, UUID correlationId, String requestId, String action,
            String reason, Instant now, UUID eventId) {
        return new LoanAuditEntry(
                UUID.randomUUID(), eventId, "Loan", funding.id().toString(), funding.version(),
                action, "SUCCESS", actor.id(), actor.role(), correlationId.toString(),
                requestId, businessReference(funding), reason,
                "{\"source\":\"loan-service\",\"schemaVersion\":1,\"currency\":\"IDR\"}", now);
    }

    private static String businessReference(LoanFunding funding) {
        return "LOAN-" + funding.id();
    }

    private static String fundedEventPayload(
            LoanFunding funding, UUID eventId, UUID correlationId, Instant occurredAt) {
        return LoanLedgerEventJson.serialize(eventId, "LoanFunded", funding.id(), funding.version(),
                businessReference(funding), occurredAt, correlationId, Map.of(
                        "customerId", funding.customerId(),
                        "productCode", funding.productCode(),
                        "principalAmount", funding.principalAmount().toPlainString(),
                        "currency", CURRENCY));
    }
}

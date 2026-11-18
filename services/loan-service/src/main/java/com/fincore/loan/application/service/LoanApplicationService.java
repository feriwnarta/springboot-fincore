package com.fincore.loan.application.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.fincore.loan.application.command.LoanApplicationDecisionCommand;
import com.fincore.loan.application.command.SubmitLoanApplicationCommand;
import com.fincore.loan.application.query.LoanApplicationPage;
import com.fincore.loan.domain.enums.IdempotencyStatus;
import com.fincore.loan.domain.enums.LoanApplicationStatus;
import com.fincore.loan.domain.model.IdempotencyRecord;
import com.fincore.loan.domain.model.LoanApplication;
import com.fincore.loan.domain.model.LoanAuditEntry;
import com.fincore.loan.domain.model.LoanOutboxEntry;
import com.fincore.loan.domain.model.LoanProduct;
import com.fincore.loan.domain.port.LoanApplicationRepository;
import com.fincore.loan.domain.port.LoanApplicationTransitionRepository;
import com.fincore.loan.domain.port.LoanAuditRepository;
import com.fincore.loan.domain.port.LoanIdempotencyRepository;
import com.fincore.loan.domain.port.LoanOutboxRepository;
import com.fincore.loan.domain.port.LoanProductRepository;
import com.fincore.platform.security.AuditActor;
import com.fincore.platform.security.CurrentIdentity;
import com.fincore.platform.web.ConflictException;
import com.fincore.platform.web.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
@RequiredArgsConstructor
public class LoanApplicationService {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String OPERATION = "SUBMIT_LOAN_APPLICATION";
    private static final String APPROVE_OPERATION = "APPROVE_LOAN_APPLICATION";
    private static final String REJECT_OPERATION = "REJECT_LOAN_APPLICATION";
    private static final String METADATA_JSON = "{\"source\":\"loan-service\",\"schemaVersion\":1}";
    private static final int DEFAULT_LIST_LIMIT = 50;
    private static final int MAX_LIST_LIMIT = 100;

    private final LoanProductRepository productRepository;
    private final LoanApplicationRepository applicationRepository;
    private final LoanApplicationTransitionRepository transitionRepository;
    private final LoanAuditRepository auditRepository;
    private final LoanIdempotencyRepository idempotencyRepository;
    private final LoanOutboxRepository outboxRepository;
    private final CurrentIdentity currentIdentity;

    public LoanApplicationPage list(
            Integer requestedLimit,
            String cursor,
            LoanApplicationStatus status,
            String requestedCustomerId,
            String requestedProductCode) {
        AuditActor actor = AuditActor.from(currentIdentity, "LOAN_OPERATOR", "LOAN_APPROVER", "CUSTOMER");
        int limit = requestedLimit == null ? DEFAULT_LIST_LIMIT : requestedLimit;
        if (limit < 1 || limit > MAX_LIST_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIST_LIMIT);
        }

        String customerId = blankToNull(requestedCustomerId);
        if ("CUSTOMER".equals(actor.role())) {
            customerId = currentIdentity.customerId()
                    .orElseThrow(() -> new AccessDeniedException("Customer identity is missing customer_id"));
            if (requestedCustomerId != null && !customerId.equals(requestedCustomerId)) {
                throw new AccessDeniedException("Customer access denied");
            }
        }
        String productCode = blankToNull(requestedProductCode);
        ApplicationCursor before = decodeCursor(cursor);
        List<LoanApplication> candidates = applicationRepository.findPage(
                customerId, status, productCode, before == null ? null : before.createdAt(),
                before == null ? null : before.id(), limit + 1);
        boolean hasNext = candidates.size() > limit;
        List<LoanApplication> items = hasNext ? candidates.subList(0, limit) : candidates;
        String nextCursor = hasNext ? encodeCursor(items.getLast()) : null;
        return new LoanApplicationPage(items, nextCursor, hasNext);
    }

    @Transactional
    public LoanApplication submit(SubmitLoanApplicationCommand command) {
        AuditActor actor = AuditActor.from(currentIdentity, "LOAN_OPERATOR", "LOAN_APPROVER", "CUSTOMER");
        currentIdentity.requireCustomerAccess(command.customerId(), "LOAN_OPERATOR", "LOAN_APPROVER");
        IdempotencyRecord requested = new IdempotencyRecord(
                command.customerId(), OPERATION, command.idempotencyKey(), fingerprintFor(command),
                null, IdempotencyStatus.IN_PROGRESS);
        var existing = findIdempotencyRecord(requested);
        if (existing.isPresent()) {
            return replaySubmission(requested, existing.get());
        }

        LoanProduct product = productRepository.findActiveByCode(command.productCode())
                .orElseThrow(() -> new ResourceNotFoundException("Active loan product was not found"));
        if (command.principalAmount().compareTo(product.minAmount()) < 0
                || command.principalAmount().compareTo(product.maxAmount()) > 0) {
            throw new IllegalArgumentException("principalAmount must be within the product amount range");
        }

        if (!idempotencyRepository.createIfAbsent(requested)) {
            return replaySubmission(requested, requireIdempotencyRecord(requested));
        }

        Instant now = Instant.now();
        UUID applicationId = UUID.randomUUID();
        String businessReference = "APP-" + applicationId;
        LoanApplication application = new LoanApplication(
                applicationId,
                command.customerId(),
                product.code(),
                command.principalAmount(),
                command.tenorMonths(),
                LoanApplicationStatus.SUBMITTED,
                1,
                now,
                null,
                null,
                actor.id(),
                businessReference);

        UUID eventId = UUID.randomUUID();
        applicationRepository.save(application);
        auditRepository.append(new LoanAuditEntry(
                UUID.randomUUID(), eventId, "LoanApplication", application.id().toString(), 1,
                "SUBMIT", "SUCCESS", actor.id(), actor.role(),
                command.correlationId(), command.requestId(), businessReference, null,
                METADATA_JSON, now));
        outboxRepository.append(new LoanOutboxEntry(
                eventId, application.id(), 1, "LoanSubmitted", 1, businessReference,
                eventPayload(application), now));
        idempotencyRepository.markSucceeded(requested, application.id().toString());
        return application;
    }

    @Transactional
    public LoanApplication approve(LoanApplicationDecisionCommand command) {
        return decide(command, LoanApplicationStatus.APPROVED, "APPROVE", "LoanApproved", APPROVE_OPERATION);
    }

    @Transactional
    public LoanApplication reject(LoanApplicationDecisionCommand command) {
        return decide(command, LoanApplicationStatus.REJECTED, "REJECT", "LoanRejected", REJECT_OPERATION);
    }

    public static String fingerprintFor(SubmitLoanApplicationCommand command) {
        String canonical = String.join("|",
                command.customerId(), command.productCode(), command.principalAmount().toPlainString(),
                Integer.toString(command.tenorMonths()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public static String fingerprintFor(LoanApplicationDecisionCommand command) {
        String canonical = canonicalField(command.applicationId().toString())
                + canonicalField(command.reasonCode())
                + canonicalField(command.reason());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String canonicalField(String value) {
        return value == null ? "-1:" : value.length() + ":" + value;
    }

    private LoanApplication decide(
            LoanApplicationDecisionCommand command,
            LoanApplicationStatus targetStatus,
            String action,
            String eventType,
            String operation) {
        AuditActor actor = AuditActor.from(currentIdentity, "LOAN_APPROVER");
        validateDecisionText(command);
        LoanApplication current = applicationRepository.findById(command.applicationId())
                .orElseThrow(() -> new ResourceNotFoundException("Loan application was not found"));
        currentIdentity.requireCustomerAccess(current.customerId());
        IdempotencyRecord requested = new IdempotencyRecord(
                command.applicationId().toString(), operation, command.idempotencyKey(),
                fingerprintFor(command), null, IdempotencyStatus.IN_PROGRESS);
        if (!idempotencyRepository.createIfAbsent(requested)) {
            return replayDecision(requested, current.customerId());
        }
        if (current.status() != LoanApplicationStatus.SUBMITTED) {
            throw new ConflictException("Only submitted loan applications can be decided");
        }

        Instant transitionedAt = Instant.now();
        if (!transitionRepository.transition(current.id(), current.version(), targetStatus, transitionedAt)) {
            throw new ConflictException("Loan application was changed by another request");
        }
        LoanApplication next = new LoanApplication(
                current.id(), current.customerId(), current.productCode(), current.principalAmount(),
                current.tenorMonths(), targetStatus, current.version() + 1, current.createdAt(),
                targetStatus == LoanApplicationStatus.APPROVED ? transitionedAt : null,
                targetStatus == LoanApplicationStatus.REJECTED ? transitionedAt : null,
                actor.id(), current.businessReference());
        UUID eventId = UUID.randomUUID();
        auditRepository.append(new LoanAuditEntry(
                UUID.randomUUID(), eventId, "LoanApplication", next.id().toString(), next.version(),
                action, "SUCCESS", actor.id(), actor.role(), command.correlationId(),
                command.requestId(), next.businessReference(), command.reasonCode(), decisionMetadata(command),
                transitionedAt));
        outboxRepository.append(new LoanOutboxEntry(
                eventId, next.id(), next.version(), eventType, 1, next.businessReference(),
                decisionPayload(next, eventType, command), transitionedAt));
        idempotencyRepository.markSucceeded(requested, next.id().toString());
        return next;
    }

    private Optional<IdempotencyRecord> findIdempotencyRecord(IdempotencyRecord requested) {
        return idempotencyRepository.findByKey(requested.callerScope(), requested.operation(), requested.idemKey());
    }

    private IdempotencyRecord requireIdempotencyRecord(IdempotencyRecord requested) {
        return findIdempotencyRecord(requested)
                .orElseThrow(() -> new ConflictException("Idempotency request is being processed"));
    }

    private LoanApplication replaySubmission(IdempotencyRecord requested, IdempotencyRecord existing) {
        LoanApplication application = loadReplayedApplication(IdempotencyReplay.resultId(requested, existing));
        if (!requested.callerScope().equals(application.customerId())) {
            throw new AccessDeniedException("Customer access denied");
        }
        return application;
    }

    private LoanApplication replayDecision(IdempotencyRecord requested, String customerId) {
        UUID resultId = IdempotencyReplay.resultId(requested, requireIdempotencyRecord(requested));
        if (!requested.callerScope().equals(resultId.toString())) {
            throw new ConflictException("Idempotency result is unavailable");
        }
        LoanApplication application = loadReplayedApplication(resultId);
        if (!customerId.equals(application.customerId())) {
            throw new AccessDeniedException("Customer access denied");
        }
        return application;
    }

    private LoanApplication loadReplayedApplication(UUID applicationId) {
        return applicationRepository.findById(applicationId)
                .filter(application -> applicationId.equals(application.id()))
                .orElseThrow(() -> new ConflictException("Idempotency result is unavailable"));
    }

    private static String eventPayload(LoanApplication application) {
        return LoanLedgerEventJson.serializeObject(Map.of(
                "applicationId", application.id().toString(),
                "eventType", "LoanSubmitted",
                "schemaVersion", 1,
                "aggregateVersion", 1,
                "businessReference", application.businessReference(),
                "customerId", application.customerId(),
                "productCode", application.productCode(),
                "principalAmount", application.principalAmount().toPlainString(),
                "tenorMonths", application.tenorMonths(),
                "createdAt", application.createdAt().toString()));
    }

    private static void validateDecisionText(LoanApplicationDecisionCommand command) {
        if (Objects.toString(command.reasonCode(), "").indexOf(0) >= 0
                || Objects.toString(command.reason(), "").indexOf(0) >= 0) {
            throw new IllegalArgumentException("Decision reason fields must not contain a NUL character");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String encodeCursor(LoanApplication application) {
        String value = application.createdAt() + "|" + application.id();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static ApplicationCursor decodeCursor(String cursor) {
        if (blankToNull(cursor) == null) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] fields = decoded.split("\\|", -1);
            if (fields.length != 2) {
                throw new IllegalArgumentException("cursor must contain a timestamp and application id");
            }
            return new ApplicationCursor(Instant.parse(fields[0]), UUID.fromString(fields[1]));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("cursor must be a valid opaque cursor", exception);
        }
    }

    private record ApplicationCursor(Instant createdAt, UUID id) {
    }

    private static String decisionMetadata(LoanApplicationDecisionCommand command) {
        ObjectNode metadata = JSON.createObjectNode()
                .put("source", "loan-service")
                .put("schemaVersion", 1);
        addDecisionReason(metadata, command);
        return JSON.writeValueAsString(metadata);
    }

    private static String decisionPayload(
            LoanApplication application,
            String eventType,
            LoanApplicationDecisionCommand command) {
        ObjectNode payload = JSON.createObjectNode()
                .put("applicationId", application.id().toString())
                .put("eventType", eventType)
                .put("schemaVersion", 1)
                .put("aggregateVersion", application.version())
                .put("businessReference", application.businessReference())
                .put("customerId", application.customerId())
                .put("productCode", application.productCode())
                .put("principalAmount", application.principalAmount().toPlainString())
                .put("tenorMonths", application.tenorMonths())
                .put("createdAt", application.createdAt().toString());
        addDecisionReason(payload, command);
        return JSON.writeValueAsString(payload);
    }

    private static void addDecisionReason(ObjectNode node, LoanApplicationDecisionCommand command) {
        if (command.reasonCode() != null || command.reason() != null) {
            node.put("reasonCode", Objects.toString(command.reasonCode(), ""))
                    .put("reason", Objects.toString(command.reason(), ""));
        }
    }
}

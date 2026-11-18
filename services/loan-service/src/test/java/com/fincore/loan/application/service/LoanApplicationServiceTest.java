package com.fincore.loan.application.service;

import static com.fincore.loan.testsupport.JwtTestIdentity.authenticate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.fincore.loan.application.command.LoanApplicationDecisionCommand;
import com.fincore.loan.application.command.SubmitLoanApplicationCommand;
import com.fincore.loan.application.query.LoanApplicationPage;
import com.fincore.loan.domain.enums.IdempotencyStatus;
import com.fincore.loan.domain.enums.InterestType;
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
import com.fincore.platform.security.SpringCurrentIdentity;
import com.fincore.platform.web.ConflictException;
import com.fincore.platform.web.ResourceNotFoundException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

class LoanApplicationServiceTest {

    private static final String PRODUCT_CODE = "PERSONAL_FLAT";
    private static final String OWNER = "customer-001";
    private static final String OTHER_CUSTOMER = "customer-002";
    private static final BigDecimal AMOUNT = new BigDecimal("1000000.0000");
    private static final Instant SUBMITTED_AT = Instant.parse("2026-11-17T03:00:00Z");

    private final LoanProductRepository productRepository = mock(LoanProductRepository.class);
    private final LoanApplicationRepository applicationRepository = mock(LoanApplicationRepository.class);
    private final LoanApplicationTransitionRepository transitionRepository = mock(LoanApplicationTransitionRepository.class);
    private final LoanAuditRepository auditRepository = mock(LoanAuditRepository.class);
    private final LoanIdempotencyRepository idempotencyRepository = mock(LoanIdempotencyRepository.class);
    private final LoanOutboxRepository outboxRepository = mock(LoanOutboxRepository.class);
    private final LoanApplicationService service = new LoanApplicationService(
            productRepository, applicationRepository, transitionRepository, auditRepository,
            idempotencyRepository, outboxRepository, new SpringCurrentIdentity());

    @BeforeEach
    void authenticateApprover() {
        authenticate("approver-001", null, "LOAN_APPROVER");
    }

    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @CsvSource({"CUSTOMER, customer-001", "LOAN_OPERATOR, customer-001", "LOAN_OPERATOR, customer-002",
            "LOAN_APPROVER, customer-002"})
    void authorizedSubmitUsesVerifiedAuditActor(String role, String customerId) {
        authenticate("jwt-subject", "CUSTOMER".equals(role) ? OWNER : null, role);
        stubActiveProduct();
        when(idempotencyRepository.createIfAbsent(any())).thenReturn(true);

        LoanApplication application = service.submit(submission(customerId));

        assertThat(application.status()).isEqualTo(LoanApplicationStatus.SUBMITTED);
        assertThat(application.customerId()).isEqualTo(customerId);
        assertThat(application.actorId()).isEqualTo("jwt-subject");
        verify(applicationRepository).save(application);
        verifyActor("jwt-subject", role);
        verify(outboxRepository).append(any());
        verify(idempotencyRepository).markSucceeded(any(), eq(application.id().toString()));
    }

    @Test
    void loanSubmittedPayloadEscapesProductCodeBeforePersistingTheOutboxEvent() {
        authenticate("jwt-subject", OWNER, "CUSTOMER");
        String unsafeProductCode = "PERSONAL\"\\\t\u0001";
        when(productRepository.findActiveByCode(unsafeProductCode)).thenReturn(Optional.of(new LoanProduct(
                UUID.randomUUID(), unsafeProductCode, InterestType.FLAT, new BigDecimal("0.120000"), AMOUNT,
                new BigDecimal("50000000.0000"), true, 1, SUBMITTED_AT)));
        when(idempotencyRepository.createIfAbsent(any())).thenReturn(true);

        LoanApplication application = service.submit(new SubmitLoanApplicationCommand(
                OWNER, unsafeProductCode, AMOUNT, 12, "unsafe-product-idem", "spoofed-subject",
                "LOAN_OPERATOR", "corr-001", "req-unsafe-product"));

        var outbox = ArgumentCaptor.forClass(LoanOutboxEntry.class);
        verify(outboxRepository).append(outbox.capture());
        var payload = new JsonMapper().readTree(outbox.getValue().payloadJson());
        assertThat(payload.path("productCode").asString()).isEqualTo(unsafeProductCode);
        assertThat(payload.path("applicationId").asString()).isEqualTo(application.id().toString());
        verify(applicationRepository).save(application);
        verify(idempotencyRepository).markSucceeded(any(), eq(application.id().toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "SAVINGS_OPERATOR", "CUSTOMER_WITH_SAVINGS_OPERATOR"})
    void deniesUnauthorizedSubmissionBeforeAnyRepositoryAccess(String identity) {
        String customerId = "SAVINGS_OPERATOR".equals(identity) ? OWNER : OTHER_CUSTOMER;
        if ("CUSTOMER_WITH_SAVINGS_OPERATOR".equals(identity)) {
            authenticate("jwt-subject", OWNER, "CUSTOMER", "SAVINGS_OPERATOR");
        } else {
            authenticate("jwt-subject", OWNER, identity);
        }
        stubActiveProduct();

        assertThatThrownBy(() -> service.submit(submission(customerId))).isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(productRepository, applicationRepository, transitionRepository,
                idempotencyRepository, auditRepository, outboxRepository);
    }

    @ParameterizedTest
    @CsvSource({"CUSTOMER, true", "CUSTOMER, false", "SAVINGS_OPERATOR, true", "SAVINGS_OPERATOR, false",
            "LOAN_OPERATOR, true", "LOAN_OPERATOR, false"})
    void deniesDecisionsWithoutTheApproverRoleBeforeRepositoryAccess(String role, boolean approve) {
        authenticate("jwt-subject", OWNER, role);
        var submitted = application(OWNER, LoanApplicationStatus.SUBMITTED);
        when(applicationRepository.findById(submitted.id())).thenReturn(Optional.of(submitted));

        assertThatThrownBy(() -> {
            if (approve) {
                service.approve(decision(submitted.id()));
            } else {
                service.reject(decision(submitted.id()));
            }
        }).isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(applicationRepository, transitionRepository, idempotencyRepository,
                auditRepository, outboxRepository);
    }

    @Test
    void replaysSuccessfulSubmissionEvenWhenTheProductIsInactive() {
        authenticate("jwt-subject", OWNER, "CUSTOMER");
        var command = submission(OWNER);
        var existing = application(OWNER, LoanApplicationStatus.SUBMITTED);
        stubSubmissionReplay(command, existing);

        assertThat(service.submit(command)).isEqualTo(existing);

        verifyNoInteractions(productRepository, auditRepository, outboxRepository);
        verify(idempotencyRepository, never()).createIfAbsent(any());
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void replaysTheWinnerWhenAnotherRequestClaimsTheSubmissionKey() {
        stubActiveProduct();
        var command = submission(OWNER);
        var existing = application(OWNER, LoanApplicationStatus.SUBMITTED);
        when(idempotencyRepository.findByKey(OWNER, "SUBMIT_LOAN_APPLICATION", command.idempotencyKey()))
                .thenReturn(Optional.empty(), Optional.of(successfulSubmission(command, existing)));
        when(applicationRepository.findById(existing.id())).thenReturn(Optional.of(existing));

        assertThat(service.submit(command)).isEqualTo(existing);

        verify(idempotencyRepository).createIfAbsent(any());
        verifyNoInteractions(auditRepository, outboxRepository);
        verify(applicationRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "LOAN_OPERATOR"})
    void deniesSubmissionReplayWhenTheStoredResultBelongsToAnotherCustomer(String role) {
        authenticate("jwt-subject", OWNER, role);
        stubActiveProduct();
        var command = submission(OWNER);
        stubSubmissionReplay(command, application(OTHER_CUSTOMER, LoanApplicationStatus.SUBMITTED));

        assertThatThrownBy(() -> service.submit(command)).isInstanceOf(AccessDeniedException.class);

        verify(idempotencyRepository, never()).createIfAbsent(any());
        verifyNoInteractions(auditRepository, outboxRepository);
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void inactiveProductDoesNotClaimAnUnusedSubmissionKey() {
        assertThatThrownBy(() -> service.submit(submission(OWNER))).isInstanceOf(ResourceNotFoundException.class);

        verify(idempotencyRepository, never()).createIfAbsent(any());
        verifyNoInteractions(applicationRepository, auditRepository, outboxRepository);
    }

    @Test
    void customerListIsRestrictedToTheCustomerIdFromTheVerifiedToken() {
        authenticate("jwt-subject", OWNER, "CUSTOMER");
        LoanApplication application = application(OWNER, LoanApplicationStatus.SUBMITTED);
        when(applicationRepository.findPage(OWNER, null, null, null, null, 51))
                .thenReturn(java.util.List.of(application));

        LoanApplicationPage page = service.list(50, null, null, null, null);

        assertThat(page.items()).containsExactly(application);
        verify(applicationRepository).findPage(OWNER, null, null, null, null, 51);
    }

    @Test
    void approvesSubmittedApplicationAndUsesVerifiedAuditActor() {
        var submitted = application(OTHER_CUSTOMER, LoanApplicationStatus.SUBMITTED);
        stubTransition(submitted, LoanApplicationStatus.APPROVED);

        var approved = service.approve(decision(submitted.id()));

        assertThat(approved.status()).isEqualTo(LoanApplicationStatus.APPROVED);
        assertThat(approved.version()).isEqualTo(2);
        assertThat(approved.approvedAt()).isNotNull();
        assertThat(approved.actorId()).isEqualTo("approver-001");
        var audit = verifyActor("approver-001", "LOAN_APPROVER");
        assertThat(audit.action()).isEqualTo("APPROVE");
        var json = new JsonMapper();
        var metadata = json.readTree(audit.metadataJson());
        assertThat(metadata.size()).isEqualTo(2);
        assertThat(metadata.path("source").asString()).isEqualTo("loan-service");
        assertThat(metadata.path("schemaVersion").asInt()).isEqualTo(1);
        var outbox = ArgumentCaptor.forClass(LoanOutboxEntry.class);
        verify(outboxRepository).append(outbox.capture());
        assertThat(outbox.getValue().eventType()).isEqualTo("LoanApproved");
        assertThat(json.readTree(outbox.getValue().payloadJson()).size()).isEqualTo(10);
        verify(idempotencyRepository).markSucceeded(any(), eq(submitted.id().toString()));
    }

    @Test
    void rejectsSubmittedApplicationAndIncludesReasonInAuditAndOutbox() {
        var submitted = application(OWNER, LoanApplicationStatus.SUBMITTED);
        stubTransition(submitted, LoanApplicationStatus.REJECTED);

        String reasonCode = "CREDIT\tPOLICY\f";
        String reason = "Debt service ratio\t exceeds \"limit\"\r\n\\\001";
        var rejected = service.reject(new LoanApplicationDecisionCommand(
                submitted.id(), "reject-idem", "spoofed-subject", "SAVINGS_OPERATOR",
                "corr-reject", "req-reject", reasonCode, reason));

        assertThat(rejected.status()).isEqualTo(LoanApplicationStatus.REJECTED);
        assertThat(rejected.version()).isEqualTo(2);
        assertThat(rejected.rejectedAt()).isNotNull();
        var audit = verifyActor("approver-001", "LOAN_APPROVER");
        assertThat(audit.action()).isEqualTo("REJECT");
        assertThat(audit.reasonCode()).isEqualTo(reasonCode);
        var json = new JsonMapper();
        var metadata = json.readTree(audit.metadataJson());
        assertThat(metadata.size()).isEqualTo(4);
        assertThat(metadata.path("source").asString()).isEqualTo("loan-service");
        assertThat(metadata.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(metadata.path("reasonCode").asString()).isEqualTo(reasonCode);
        assertThat(metadata.path("reason").asString()).isEqualTo(reason);
        var outbox = ArgumentCaptor.forClass(LoanOutboxEntry.class);
        verify(outboxRepository).append(outbox.capture());
        assertThat(outbox.getValue().eventType()).isEqualTo("LoanRejected");
        var payload = json.readTree(outbox.getValue().payloadJson());
        assertThat(payload.size()).isEqualTo(12);
        assertThat(payload.path("applicationId").asString()).isEqualTo(submitted.id().toString());
        assertThat(payload.path("eventType").asString()).isEqualTo("LoanRejected");
        assertThat(payload.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(payload.path("aggregateVersion").asLong()).isEqualTo(2);
        assertThat(payload.path("businessReference").asString()).isEqualTo(submitted.businessReference());
        assertThat(payload.path("productCode").asString()).isEqualTo(PRODUCT_CODE);
        assertThat(payload.path("principalAmount").asString()).isEqualTo(AMOUNT.toPlainString());
        assertThat(payload.path("tenorMonths").asInt()).isEqualTo(12);
        assertThat(payload.path("reasonCode").asString()).isEqualTo(reasonCode);
        assertThat(payload.path("reason").asString()).isEqualTo(reason);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rejectsNulInDecisionTextBeforeClaimingTheKey(boolean nulInReasonCode) {
        var submitted = application(OWNER, LoanApplicationStatus.SUBMITTED);
        stubTransition(submitted, LoanApplicationStatus.REJECTED);
        String reasonCode = nulInReasonCode ? "CREDIT\0POLICY" : "CREDIT_POLICY";
        String reason = nulInReasonCode ? "Over limit" : "Over\0limit";

        assertThatThrownBy(() -> service.reject(new LoanApplicationDecisionCommand(
                submitted.id(), "reject-idem", "spoofed-subject", "LOAN_APPROVER",
                "corr-reject", "req-reject", reasonCode, reason)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(idempotencyRepository, transitionRepository, auditRepository, outboxRepository);
    }

    @Test
    void rejectsDecisionForMissingApplication() {
        assertThatThrownBy(() -> service.reject(decision(UUID.randomUUID())))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(transitionRepository, auditRepository, outboxRepository, idempotencyRepository);
    }

    @Test
    void rejectsDecisionWhenApplicationIsNotSubmitted() {
        var approved = application(OWNER, LoanApplicationStatus.APPROVED);
        when(applicationRepository.findById(approved.id())).thenReturn(Optional.of(approved));
        when(idempotencyRepository.createIfAbsent(any())).thenReturn(true);

        assertThatThrownBy(() -> service.approve(decision(approved.id()))).isInstanceOf(ConflictException.class);

        verifyNoInteractions(transitionRepository, auditRepository, outboxRepository);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void replaysSuccessfulDecisionWithoutDuplicateSideEffects(boolean approve) {
        var current = application(OWNER, approve ? LoanApplicationStatus.APPROVED : LoanApplicationStatus.REJECTED);
        var command = decision(current.id());
        when(applicationRepository.findById(current.id())).thenReturn(Optional.of(current));
        stubDecisionReplay(command, approve, current.id().toString());

        assertThat(decide(command, approve)).isEqualTo(current);

        verifyNoInteractions(transitionRepository, auditRepository, outboxRepository);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rejectsTamperedDecisionReplayEvenForAnotherApplicationOfTheSameCustomer(boolean approve) {
        var current = application(OWNER, approve ? LoanApplicationStatus.APPROVED : LoanApplicationStatus.REJECTED);
        var other = application(OWNER, current.status());
        var command = decision(current.id());
        when(applicationRepository.findById(current.id())).thenReturn(Optional.of(current));
        when(applicationRepository.findById(other.id())).thenReturn(Optional.of(other));
        stubDecisionReplay(command, approve, other.id().toString());

        assertThatThrownBy(() -> decide(command, approve)).isInstanceOf(ConflictException.class);

        verifyNoInteractions(transitionRepository, auditRepository, outboxRepository);
    }

    @Test
    void rejectsDecisionReplayWhenTheCustomerScopeChangesBetweenReads() {
        var current = application(OWNER, LoanApplicationStatus.APPROVED);
        var command = decision(current.id());
        var differentOwner = new LoanApplication(current.id(), OTHER_CUSTOMER, current.productCode(),
                current.principalAmount(), current.tenorMonths(), current.status(), current.version(),
                current.createdAt(), current.approvedAt(), current.rejectedAt(), current.actorId(),
                current.businessReference());
        when(applicationRepository.findById(current.id()))
                .thenReturn(Optional.of(current), Optional.of(differentOwner));
        stubDecisionReplay(command, true, current.id().toString());

        assertThatThrownBy(() -> service.approve(command)).isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(transitionRepository, auditRepository, outboxRepository);
    }

    @Test
    void rejectsMalformedDecisionReplayReferenceAsAConflict() {
        var current = application(OWNER, LoanApplicationStatus.APPROVED);
        var command = decision(current.id());
        when(applicationRepository.findById(current.id())).thenReturn(Optional.of(current));
        stubDecisionReplay(command, true, "invalid-reference");

        assertThatThrownBy(() -> service.approve(command)).isInstanceOf(ConflictException.class);

        verifyNoInteractions(transitionRepository, auditRepository, outboxRepository);
    }

    @Test
    void decisionFingerprintDistinguishesDelimiterCollisions() {
        UUID applicationId = UUID.randomUUID();
        var first = new LoanApplicationDecisionCommand(
                applicationId, "idem-1", "actor", "LOAN_APPROVER", "corr", "req",
                "reason|code", "reason");
        var second = new LoanApplicationDecisionCommand(
                applicationId, "idem-1", "actor", "LOAN_APPROVER", "corr", "req",
                "reason", "code|reason");

        assertThat(LoanApplicationService.fingerprintFor(first))
                .isNotEqualTo(LoanApplicationService.fingerprintFor(second));
    }

    private LoanApplication decide(LoanApplicationDecisionCommand command, boolean approve) {
        return approve ? service.approve(command) : service.reject(command);
    }

    private void stubDecisionReplay(LoanApplicationDecisionCommand command, boolean approve, String resultRef) {
        String scope = command.applicationId().toString();
        String operation = approve ? "APPROVE_LOAN_APPLICATION" : "REJECT_LOAN_APPLICATION";
        when(idempotencyRepository.findByKey(scope, operation, command.idempotencyKey()))
                .thenReturn(Optional.of(new IdempotencyRecord(scope, operation, command.idempotencyKey(),
                        LoanApplicationService.fingerprintFor(command), resultRef, IdempotencyStatus.SUCCEEDED)));
    }

    private void stubActiveProduct() {
        when(productRepository.findActiveByCode(PRODUCT_CODE)).thenReturn(Optional.of(new LoanProduct(
                UUID.randomUUID(), PRODUCT_CODE, InterestType.FLAT, new BigDecimal("0.120000"), AMOUNT,
                new BigDecimal("50000000.0000"), true, 1, SUBMITTED_AT)));
    }

    private void stubSubmissionReplay(SubmitLoanApplicationCommand command, LoanApplication existing) {
        when(idempotencyRepository.findByKey(command.customerId(), "SUBMIT_LOAN_APPLICATION", command.idempotencyKey()))
                .thenReturn(Optional.of(successfulSubmission(command, existing)));
        when(applicationRepository.findById(existing.id())).thenReturn(Optional.of(existing));
    }

    private static IdempotencyRecord successfulSubmission(SubmitLoanApplicationCommand command, LoanApplication existing) {
        return new IdempotencyRecord(command.customerId(), "SUBMIT_LOAN_APPLICATION", command.idempotencyKey(),
                LoanApplicationService.fingerprintFor(command), existing.id().toString(), IdempotencyStatus.SUCCEEDED);
    }

    private void stubTransition(LoanApplication current, LoanApplicationStatus next) {
        when(applicationRepository.findById(current.id())).thenReturn(Optional.of(current));
        when(idempotencyRepository.createIfAbsent(any())).thenReturn(true);
        when(transitionRepository.transition(eq(current.id()), eq(current.version()), eq(next), any())).thenReturn(true);
    }

    private LoanAuditEntry verifyActor(String subject, String role) {
        var audit = ArgumentCaptor.forClass(LoanAuditEntry.class);
        verify(auditRepository).append(audit.capture());
        assertThat(audit.getValue().actorId()).isEqualTo(subject);
        assertThat(audit.getValue().actorRole()).isEqualTo(role);
        return audit.getValue();
    }

    private static SubmitLoanApplicationCommand submission(String customerId) {
        return new SubmitLoanApplicationCommand(customerId, PRODUCT_CODE, AMOUNT, 12,
                "idem-001", "spoofed-subject", "LOAN_APPROVER", "corr-001", "req-001");
    }

    private static LoanApplicationDecisionCommand decision(UUID applicationId) {
        return new LoanApplicationDecisionCommand(applicationId, "decision-idem", "spoofed-subject",
                "LOAN_APPROVER", "corr-001", "req-001", null, null);
    }

    private static LoanApplication application(String customerId, LoanApplicationStatus status) {
        return new LoanApplication(
                UUID.randomUUID(), customerId, PRODUCT_CODE, AMOUNT, 12, status,
                status == LoanApplicationStatus.SUBMITTED ? 1 : 2, SUBMITTED_AT,
                status == LoanApplicationStatus.APPROVED ? SUBMITTED_AT.plusSeconds(60) : null,
                null, "submitter-001", "APP-001");
    }
}

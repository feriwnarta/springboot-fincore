package com.fincore.loan.application.service;

import static com.fincore.loan.testsupport.JwtTestIdentity.authenticate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.fincore.loan.application.command.RequestFundingCommand;
import com.fincore.loan.domain.enums.IdempotencyStatus;
import com.fincore.loan.domain.enums.LoanApplicationStatus;
import com.fincore.loan.domain.enums.LedgerPostingStatus;
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
import com.fincore.platform.security.SpringCurrentIdentity;
import com.fincore.platform.web.ConflictException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

class LoanFundingServiceTest {

    private final LoanApplicationRepository applications = mock(LoanApplicationRepository.class);
    private final LoanFundingRepository funding = mock(LoanFundingRepository.class);
    private final LoanAuditRepository audits = mock(LoanAuditRepository.class);
    private final LoanIdempotencyRepository idempotency = mock(LoanIdempotencyRepository.class);
    private final LoanOutboxRepository outbox = mock(LoanOutboxRepository.class);

    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @CsvSource({"LOAN_OPERATOR, customer-001", "LOAN_OPERATOR, customer-002", "LOAN_APPROVER, customer-002"})
    void authorizedFundingPersistsFundedStateAndLoanFundedOutbox(String role, String customerId) {
        authenticate("actor-001", null, role);
        LoanApplication application = stubSuccessfulFunding(customerId);

        LoanFunding result = service()
                .requestFunding(command(application.id(), false, UUID.randomUUID().toString()));

        assertThat(result.status()).isEqualTo(LoanFundingStatus.FUNDED);
        assertThat(result.customerId()).isEqualTo(customerId);
        assertThat(result.ledgerPostingStatus()).isEqualTo(LedgerPostingStatus.PENDING);
        verify(funding).save(any(LoanFunding.class));
        verify(funding).markFunded(application.id(), 1, result.fundedAt(), result.updatedAt());
        var audit = ArgumentCaptor.forClass(LoanAuditEntry.class);
        verify(audits).append(audit.capture());
        assertThat(audit.getValue().actorId()).isEqualTo("actor-001");
        assertThat(audit.getValue().actorRole()).isEqualTo(role);
        verify(outbox).append(org.mockito.ArgumentMatchers.argThat(event ->
                event.eventType().equals("LoanFunded")
                        && event.payloadJson().contains("LoanFunded")));
        verify(idempotency).markSucceeded(any(), org.mockito.ArgumentMatchers.eq(application.id().toString()));
    }

    @Test
    void failedSimulationDoesNotPublishLoanFunded() {
        authenticate("actor-001", null, "LOAN_OPERATOR");
        LoanApplication application = approvedApplication("customer-001");
        when(applications.findById(application.id())).thenReturn(Optional.of(application));
        when(idempotency.createIfAbsent(any())).thenReturn(true);
        when(funding.findByApplicationId(application.id())).thenReturn(Optional.empty());
        when(funding.markFailed(any(), any(Long.class), any())).thenReturn(true);

        LoanFunding result = service()
                .requestFunding(command(application.id(), true, UUID.randomUUID().toString()));

        assertThat(result.status()).isEqualTo(LoanFundingStatus.FUNDING_FAILED);
        verify(funding).markFailed(application.id(), 1, result.updatedAt());
        var audit = ArgumentCaptor.forClass(LoanAuditEntry.class);
        verify(audits).append(audit.capture());
        assertThat(audit.getValue().actorId()).isEqualTo("actor-001");
        assertThat(audit.getValue().actorRole()).isEqualTo("LOAN_OPERATOR");
        verifyNoInteractions(outbox);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "SAVINGS_OPERATOR"})
    void deniesFundingWithoutLoanStaffRoleEvenForTheMatchingCustomer(String role) {
        authenticate("actor-001", "customer-001", role);
        LoanApplication application = stubSuccessfulFunding("customer-001");
        var command = command(application.id(), false, UUID.randomUUID().toString());

        assertThatThrownBy(() -> service().requestFunding(command)).isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(applications, idempotency, funding, audits, outbox);
    }

    @ParameterizedTest
    @ValueSource(strings = {"external-correlation", "x\"\\\n\t"})
    void storedFundingEventHasAValidLedgerCorrelationId(String invalidCorrelation) {
        authenticate("actor-001", null, "LOAN_OPERATOR");
        LoanApplication application = stubSuccessfulFunding("customer-001");

        service().requestFunding(command(application.id(), false, invalidCorrelation));

        var outboxEntry = ArgumentCaptor.forClass(LoanOutboxEntry.class);
        verify(outbox).append(outboxEntry.capture());
        var json = new JsonMapper().readTree(outboxEntry.getValue().payloadJson());
        String correlationId = json.path("correlationId").asString();
        assertThat(UUID.fromString(correlationId).toString()).isEqualTo(correlationId);
        assertThat(json.path("eventType").asString()).isEqualTo("LoanFunded");
        assertThat(json.path("payload").path("principalAmount").asString()).isEqualTo("1000000.0000");
        verify(audits).append(org.mockito.ArgumentMatchers.argThat(audit ->
                correlationId.equals(audit.correlationId())));
    }

    @Test
    void replaysFundingForTheAuthorizedApplicationByItsStoredFundingId() {
        authenticate("actor-001", null, "LOAN_OPERATOR");
        var application = approvedApplication("customer-001");
        var stored = funded(UUID.randomUUID(), application.id(), application.customerId());
        var command = stubFundingReplay(application, stored.id().toString());
        when(funding.findById(stored.id())).thenReturn(Optional.of(stored));

        assertThat(service().requestFunding(command)).isEqualTo(stored);

        verifyNoInteractions(audits, outbox);
        verify(funding).findById(stored.id());
        verifyNoMoreInteractions(funding);
    }

    @Test
    void rejectsTamperedFundingReplayForAnotherApplicationOfTheSameCustomer() {
        authenticate("actor-001", null, "LOAN_OPERATOR");
        var application = approvedApplication("customer-001");
        var other = approvedApplication(application.customerId());
        var stored = funded(other.id(), other.id(), other.customerId());
        var command = stubFundingReplay(application, stored.id().toString());
        when(funding.findById(stored.id())).thenReturn(Optional.of(stored));
        when(funding.findByApplicationId(stored.id())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service().requestFunding(command)).isInstanceOf(ConflictException.class);

        verifyNoInteractions(audits, outbox);
    }

    @Test
    void rejectsFundingReplayWhenTheCustomerDoesNotMatchTheAuthorizedApplication() {
        authenticate("actor-001", null, "LOAN_APPROVER");
        var application = approvedApplication("customer-001");
        var stored = funded(application.id(), application.id(), "customer-002");
        var command = stubFundingReplay(application, stored.id().toString());
        when(funding.findById(stored.id())).thenReturn(Optional.of(stored));
        when(funding.findByApplicationId(stored.id())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service().requestFunding(command)).isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(audits, outbox);
    }

    @Test
    void rejectsFundingReplayWhenTheLoadedIdDoesNotMatchTheReference() {
        authenticate("actor-001", null, "LOAN_OPERATOR");
        var application = approvedApplication("customer-001");
        var stored = funded(UUID.randomUUID(), application.id(), application.customerId());
        var command = stubFundingReplay(application, application.id().toString());
        when(funding.findById(application.id())).thenReturn(Optional.of(stored));
        when(funding.findByApplicationId(application.id())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service().requestFunding(command)).isInstanceOf(ConflictException.class);

        verifyNoInteractions(audits, outbox);
    }

    @Test
    void rejectsMalformedFundingReplayReferenceAsAConflict() {
        authenticate("actor-001", null, "LOAN_OPERATOR");
        var application = approvedApplication("customer-001");
        var command = stubFundingReplay(application, "invalid-reference");

        assertThatThrownBy(() -> service().requestFunding(command)).isInstanceOf(ConflictException.class);

        verifyNoInteractions(funding, audits, outbox);
    }

    private RequestFundingCommand stubFundingReplay(LoanApplication application, String resultRef) {
        var command = command(application.id(), false, UUID.randomUUID().toString());
        String scope = application.id().toString();
        String operation = "REQUEST_LOAN_FUNDING";
        when(applications.findById(application.id())).thenReturn(Optional.of(application));
        when(idempotency.findByKey(scope, operation, command.idempotencyKey()))
                .thenReturn(Optional.of(new IdempotencyRecord(scope, operation, command.idempotencyKey(),
                        LoanFundingService.fingerprintFor(command), resultRef, IdempotencyStatus.SUCCEEDED)));
        return command;
    }

    private static LoanFunding funded(UUID id, UUID applicationId, String customerId) {
        return new LoanFunding(id, applicationId, customerId, "PERSONAL_FLAT", new BigDecimal("1000000.0000"),
                LoanFundingStatus.FUNDED, LedgerPostingStatus.POSTED, 2, Instant.EPOCH, Instant.EPOCH, Instant.EPOCH);
    }

    private LoanApplication stubSuccessfulFunding(String customerId) {
        LoanApplication application = approvedApplication(customerId);
        when(applications.findById(application.id())).thenReturn(Optional.of(application));
        when(idempotency.createIfAbsent(any())).thenReturn(true);
        when(funding.markFunded(any(), any(Long.class), any(), any())).thenReturn(true);
        return application;
    }

    private LoanFundingService service() {
        return new LoanFundingService(applications, funding, audits, idempotency, outbox,
                new DeterministicFundingSimulator(), new SpringCurrentIdentity());
    }

    private static RequestFundingCommand command(UUID applicationId, boolean failure, String correlationId) {
        return new RequestFundingCommand(applicationId, failure, "idem-fund-001", "spoofed-subject",
                "LOAN_APPROVER", correlationId, "req-funding");
    }

    private static LoanApplication approvedApplication(String customerId) {
        return new LoanApplication(
                UUID.randomUUID(), customerId, "PERSONAL_FLAT", new BigDecimal("1000000.0000"),
                12, LoanApplicationStatus.APPROVED, 2, Instant.parse("2026-11-17T03:00:00Z"),
                Instant.parse("2026-11-17T03:05:00Z"), null, "approver-001", "APP-001");
    }
}

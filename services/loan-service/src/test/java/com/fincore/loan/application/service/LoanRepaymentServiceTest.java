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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fincore.loan.application.command.RequestRepaymentCommand;
import com.fincore.loan.domain.enums.IdempotencyStatus;
import com.fincore.loan.domain.enums.LedgerPostingStatus;
import com.fincore.loan.domain.enums.LoanFundingStatus;
import com.fincore.loan.domain.enums.RepaymentScheduleStatus;
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

class LoanRepaymentServiceTest {

    private final LoanFundingRepository fundingRepository = mock(LoanFundingRepository.class);
    private final LoanRepaymentRepository repaymentRepository = mock(LoanRepaymentRepository.class);
    private final LoanRepaymentScheduleRepository scheduleRepository = mock(LoanRepaymentScheduleRepository.class);
    private final LoanAuditRepository auditRepository = mock(LoanAuditRepository.class);
    private final LoanIdempotencyRepository idempotencyRepository = mock(LoanIdempotencyRepository.class);
    private final LoanOutboxRepository outboxRepository = mock(LoanOutboxRepository.class);

    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @CsvSource({"LOAN_OPERATOR, customer-001", "LOAN_OPERATOR, customer-002", "LOAN_APPROVER, customer-002"})
    void authorizedRepaymentAllocatesInterestBeforePrincipalAndEmitsConfirmedEvent(String role, String customerId) {
        authenticate("actor", null, role);
        UUID loanId = stubReadyForRepayment(customerId);

        Repayment result = service().request(command(loanId, UUID.randomUUID().toString()));

        assertThat(result.status().name()).isEqualTo("CONFIRMED_AWAITING_LEDGER");
        ArgumentCaptor<List<RepaymentAllocation>> captor = ArgumentCaptor.forClass(List.class);
        verify(repaymentRepository).save(any(), captor.capture());
        assertThat(captor.getValue()).extracting(RepaymentAllocation::interestAmount)
                .containsExactly(money("5"), money("3"));
        assertThat(captor.getValue()).extracting(RepaymentAllocation::principalAmount)
                .containsExactly(money("10"), money("0"));
        var audit = ArgumentCaptor.forClass(LoanAuditEntry.class);
        verify(auditRepository).append(audit.capture());
        assertThat(audit.getValue().actorId()).isEqualTo("actor");
        assertThat(audit.getValue().actorRole()).isEqualTo(role);
        verify(outboxRepository).append(any());
        verify(idempotencyRepository).markSucceeded(any(), eq(result.id().toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "SAVINGS_OPERATOR"})
    void deniesRepaymentWithoutLoanStaffRoleEvenForTheMatchingCustomer(String role) {
        authenticate("actor", "customer-001", role);
        UUID loanId = stubReadyForRepayment("customer-001");
        var command = command(loanId, UUID.randomUUID().toString());

        assertThatThrownBy(() -> service().request(command)).isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(fundingRepository, idempotencyRepository, scheduleRepository,
                repaymentRepository, auditRepository, outboxRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"external-correlation", "x\"\\\n\t"})
    void storedRepaymentEventHasAValidLedgerCorrelationId(String invalidCorrelation) {
        authenticate("actor", null, "LOAN_OPERATOR");
        UUID loanId = stubReadyForRepayment("customer-001");

        Repayment result = service().request(command(loanId, invalidCorrelation));

        var outbox = ArgumentCaptor.forClass(LoanOutboxEntry.class);
        verify(outboxRepository).append(outbox.capture());
        var json = new JsonMapper().readTree(outbox.getValue().payloadJson());
        String correlationId = json.path("correlationId").asString();
        assertThat(UUID.fromString(correlationId).toString()).isEqualTo(correlationId);
        assertThat(json.path("eventType").asString()).isEqualTo("RepaymentConfirmed");
        assertThat(json.path("payload").path("repaymentId").asString()).isEqualTo(result.id().toString());
        assertThat(json.path("payload").path("amount").asString()).isEqualTo("18.0000");
        verify(auditRepository).append(org.mockito.ArgumentMatchers.argThat(audit ->
                correlationId.equals(audit.correlationId())));
    }

    @Test
    void replaysSuccessfulRepaymentEvenWhenTheLoanIsPaidOff() {
        authenticate("actor", null, "LOAN_OPERATOR");
        UUID loanId = stubPostedFunding("customer-001", LoanFundingStatus.PAID_OFF);
        var command = command(loanId, UUID.randomUUID().toString());
        var repayment = new Repayment(UUID.randomUUID(), loanId, "REPAY-001", money("18"),
                RepaymentStatus.POSTED, "RCPT-001", "journal-001", command.idempotencyKey(),
                Instant.EPOCH, Instant.EPOCH.plusSeconds(1));
        when(idempotencyRepository.findByKey(loanId.toString(), "REQUEST_REPAYMENT", command.idempotencyKey()))
                .thenReturn(Optional.of(new IdempotencyRecord(loanId.toString(), "REQUEST_REPAYMENT",
                        command.idempotencyKey(), "18.0000", repayment.id().toString(), IdempotencyStatus.SUCCEEDED)));
        when(repaymentRepository.findById(repayment.id())).thenReturn(Optional.of(repayment));

        assertThat(service().request(command)).isEqualTo(repayment);

        verify(idempotencyRepository, never()).createIfAbsent(any());
        verify(repaymentRepository, never()).save(any(), any());
        verifyNoInteractions(scheduleRepository, auditRepository, outboxRepository);
    }

    @Test
    void rejectsMalformedRepaymentReplayReferenceBeforeReturningAResult() {
        authenticate("actor", null, "LOAN_OPERATOR");
        UUID loanId = stubPostedFunding("customer-001", LoanFundingStatus.PAID_OFF);
        var command = command(loanId, UUID.randomUUID().toString());
        when(idempotencyRepository.findByKey(loanId.toString(), "REQUEST_REPAYMENT", command.idempotencyKey()))
                .thenReturn(Optional.of(new IdempotencyRecord(
                        loanId.toString(), "REQUEST_REPAYMENT", command.idempotencyKey(),
                        command.amount().setScale(4).toPlainString(), "not-a-uuid", IdempotencyStatus.SUCCEEDED)));

        assertThatThrownBy(() -> service().request(command))
                .isInstanceOf(ConflictException.class);

        verify(repaymentRepository, never()).findById(any());
        verifyNoInteractions(scheduleRepository, auditRepository, outboxRepository);
    }

    @Test
    void rejectsRepaymentReplayForAnotherLoanOrIdempotencyKey() {
        authenticate("actor", null, "LOAN_OPERATOR");
        UUID loanId = stubPostedFunding("customer-001", LoanFundingStatus.PAID_OFF);
        UUID otherLoanId = UUID.randomUUID();
        var command = command(loanId, UUID.randomUUID().toString());
        var replayed = new Repayment(UUID.randomUUID(), otherLoanId, "REPAY-other", money("18"),
                RepaymentStatus.POSTED, "RCPT-other", "journal-other", "other-idem", Instant.EPOCH, Instant.EPOCH);
        when(idempotencyRepository.findByKey(loanId.toString(), "REQUEST_REPAYMENT", command.idempotencyKey()))
                .thenReturn(Optional.of(new IdempotencyRecord(
                        loanId.toString(), "REQUEST_REPAYMENT", command.idempotencyKey(),
                        command.amount().setScale(4).toPlainString(), replayed.id().toString(), IdempotencyStatus.SUCCEEDED)));
        when(repaymentRepository.findById(replayed.id())).thenReturn(Optional.of(replayed));

        assertThatThrownBy(() -> service().request(command))
                .isInstanceOf(ConflictException.class);

        verifyNoInteractions(scheduleRepository, auditRepository, outboxRepository);
    }

    @Test
    void paidOffLoanDoesNotClaimAnUnusedRepaymentKey() {
        authenticate("actor", null, "LOAN_OPERATOR");
        UUID loanId = stubPostedFunding("customer-001", LoanFundingStatus.PAID_OFF);

        assertThatThrownBy(() -> service().request(command(loanId, UUID.randomUUID().toString())))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Loan is not ready for repayment");

        verify(idempotencyRepository, never()).createIfAbsent(any());
        verifyNoInteractions(scheduleRepository, repaymentRepository, auditRepository, outboxRepository);
    }

    private LoanRepaymentService service() {
        return new LoanRepaymentService(fundingRepository, repaymentRepository, scheduleRepository, auditRepository,
                idempotencyRepository, outboxRepository, new SpringCurrentIdentity());
    }

    private UUID stubReadyForRepayment(String customerId) {
        UUID loanId = stubPostedFunding(customerId, LoanFundingStatus.FUNDED);
        when(idempotencyRepository.createIfAbsent(any())).thenReturn(true);
        when(scheduleRepository.findOpenForUpdate(loanId)).thenReturn(List.of(
                schedule(loanId, 1, money("10"), money("5")),
                schedule(loanId, 2, money("20"), money("3"))));
        return loanId;
    }

    private UUID stubPostedFunding(String customerId, LoanFundingStatus status) {
        UUID loanId = UUID.randomUUID();
        when(fundingRepository.findById(loanId)).thenReturn(Optional.of(new LoanFunding(
                loanId, UUID.randomUUID(), customerId, "PERSONAL_FLAT", money("100"),
                status, LedgerPostingStatus.POSTED, 2, Instant.EPOCH, Instant.EPOCH, Instant.EPOCH)));
        return loanId;
    }

    private static RequestRepaymentCommand command(UUID loanId, String correlationId) {
        return new RequestRepaymentCommand(loanId, money("18"), "idem-1", "spoofed-subject",
                "LOAN_APPROVER", correlationId, "req-1");
    }

    private static RepaymentSchedule schedule(UUID loanId, int number, BigDecimal principal, BigDecimal interest) {
        return new RepaymentSchedule(UUID.randomUUID(), loanId, number, LocalDate.of(2026, number, 1),
                principal, interest, money("0"), money("0"), RepaymentScheduleStatus.SCHEDULED, 1);
    }

    private static BigDecimal money(String amount) {
        return new BigDecimal(amount).setScale(4);
    }
}

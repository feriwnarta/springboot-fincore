package com.fincore.loan.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fincore.loan.domain.enums.RepaymentStatus;
import com.fincore.loan.domain.model.LoanAuditEntry;
import com.fincore.loan.domain.model.Repayment;
import com.fincore.loan.domain.model.RepaymentAllocation;
import com.fincore.loan.domain.model.RepaymentJournalPostedEvent;
import com.fincore.loan.domain.port.LoanAuditRepository;
import com.fincore.loan.domain.port.LoanFundingRepository;
import com.fincore.loan.domain.port.LoanProcessedEventRepository;
import com.fincore.loan.domain.port.LoanRepaymentRepository;
import com.fincore.loan.domain.port.LoanRepaymentScheduleRepository;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RepaymentJournalPostedEventHandlerTest {

    @ParameterizedTest
    @ValueSource(strings = {"repaymentId", "aggregateId", "amount", "currency", "businessReference"})
    void rejectsAnAcknowledgementThatDoesNotMatchTheLocalRepayment(String mismatch) {
        var inbox = mock(LoanProcessedEventRepository.class);
        var repayments = mock(LoanRepaymentRepository.class);
        var schedules = mock(LoanRepaymentScheduleRepository.class);
        var audit = mock(LoanAuditRepository.class);
        var funding = mock(LoanFundingRepository.class);
        UUID loanId = UUID.randomUUID();
        UUID repaymentId = UUID.randomUUID();
        when(repayments.findById(repaymentId)).thenReturn(Optional.of(repayment(repaymentId, loanId)));
        var event = event(loanId, repaymentId, mismatch);

        assertThatThrownBy(() -> handler(inbox, funding, repayments, schedules, audit)
                .handle(event))
                .isInstanceOfAny(IllegalArgumentException.class, IllegalStateException.class);

        verifyNoInteractions(inbox, schedules, funding, audit);
        verify(repayments).findById(event.repaymentId());
        verify(repayments, never()).findAllocations(any());
    }

    @Test
    void rejectsUnsupportedCurrencyBeforeClaimingOrMutating() {
        var inbox = mock(LoanProcessedEventRepository.class);
        var repayments = mock(LoanRepaymentRepository.class);
        var schedules = mock(LoanRepaymentScheduleRepository.class);
        var audit = mock(LoanAuditRepository.class);
        var funding = mock(LoanFundingRepository.class);
        UUID loanId = UUID.randomUUID();
        UUID repaymentId = UUID.randomUUID();
        when(repayments.findById(repaymentId)).thenReturn(Optional.of(repayment(repaymentId, loanId)));
        var invalidCurrency = new RepaymentJournalPostedEvent(
                UUID.randomUUID(), loanId, 1, "REPAY-1", Instant.now(), UUID.randomUUID(),
                UUID.randomUUID(), repaymentId, money("18"), "USD");

        assertThatThrownBy(() -> handler(inbox, funding, repayments, schedules, audit).handle(invalidCurrency))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(inbox, schedules, funding, audit);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void settlesRepaymentAndWritesDistinctCausalAuditRowsOnlyOnce(boolean paidOff) {
        var inbox = mock(LoanProcessedEventRepository.class);
        var repayments = mock(LoanRepaymentRepository.class);
        var schedules = mock(LoanRepaymentScheduleRepository.class);
        var audit = mock(LoanAuditRepository.class);
        var funding = mock(LoanFundingRepository.class);
        UUID repaymentId = UUID.randomUUID();
        UUID loanId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();
        when(inbox.claim(any(), any())).thenReturn(true, false);
        when(repayments.findById(repaymentId)).thenReturn(Optional.of(repayment(repaymentId, loanId)));
        when(repayments.findAllocations(repaymentId)).thenReturn(List.of(
                new RepaymentAllocation(scheduleId, money("10"), money("8"), 1)));
        when(schedules.applyAllocation(scheduleId, 1, money("10"), money("8"))).thenReturn(true);
        when(repayments.markPosted(any(), any(), any())).thenReturn(true);
        when(schedules.allPaid(loanId)).thenReturn(paidOff);
        when(funding.markPaidOff(eq(loanId), any())).thenReturn(true);

        var handler = new RepaymentJournalPostedEventHandler(inbox, funding, repayments, schedules, audit);
        var event = event(loanId, repaymentId, null);

        assertThat(handler.handle(event)).isEqualTo(LoanEventHandlingResult.PROCESSED);
        assertThat(handler.handle(event)).isEqualTo(LoanEventHandlingResult.DUPLICATE);
        verify(repayments).markPosted(eq(repaymentId), eq(event.journalId().toString()), any());
        verify(schedules).applyAllocation(scheduleId, 1, money("10"), money("8"));
        var auditRows = ArgumentCaptor.forClass(LoanAuditEntry.class);
        if (paidOff) {
            verify(funding).markPaidOff(eq(loanId), any());
            verify(audit, times(2)).append(auditRows.capture());
            assertThat(auditRows.getAllValues()).extracting(LoanAuditEntry::eventId).doesNotHaveDuplicates();
            assertThat(auditRows.getAllValues()).filteredOn(row -> row.action().equals("LOAN_PAID_OFF"))
                    .singleElement().satisfies(row -> assertThat(row.aggregateId()).isEqualTo(loanId.toString()));
        } else {
            verify(funding, never()).markPaidOff(any(), any());
            verify(audit).append(auditRows.capture());
        }
        assertThat(auditRows.getAllValues()).allSatisfy(row -> {
            new JsonMapper().readTree(row.metadataJson());
            assertThat(row.causationId()).isEqualTo(event.eventId().toString());
            assertThat(row.correlationId()).isEqualTo(event.correlationId().toString());
        });
        assertThat(auditRows.getAllValues()).filteredOn(row -> row.action().equals("RepaymentJournalPosted"))
                .singleElement().satisfies(row -> assertThat(row.eventId()).isEqualTo(event.eventId()));
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(4);
    }

    private static Repayment repayment(UUID repaymentId, UUID loanId) {
        return new Repayment(repaymentId, loanId, "REPAY-1", money("18"),
                RepaymentStatus.CONFIRMED_AWAITING_LEDGER, "RCPT-1", null, "idem-1", Instant.now(), null);
    }

    private static RepaymentJournalPostedEvent event(UUID loanId, UUID repaymentId, String mismatch) {
        UUID eventRepaymentId = "repaymentId".equals(mismatch) ? UUID.randomUUID() : repaymentId;
        UUID eventLoanId = "aggregateId".equals(mismatch) ? UUID.randomUUID() : loanId;
        String businessReference = "businessReference".equals(mismatch) ? "REPAY-tampered" : "REPAY-1";
        BigDecimal amount = "amount".equals(mismatch) ? money("19") : money("18");
        String currency = "currency".equals(mismatch) ? "USD" : "IDR";
        return new RepaymentJournalPostedEvent(
                UUID.randomUUID(), eventLoanId, 1, businessReference, Instant.now(), UUID.randomUUID(),
                UUID.randomUUID(), eventRepaymentId, amount, currency);
    }

    private static RepaymentJournalPostedEventHandler handler(
            LoanProcessedEventRepository inbox,
            LoanFundingRepository funding,
            LoanRepaymentRepository repayments,
            LoanRepaymentScheduleRepository schedules,
            LoanAuditRepository audit) {
        return new RepaymentJournalPostedEventHandler(inbox, funding, repayments, schedules, audit);
    }
}

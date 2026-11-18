package com.fincore.loan.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fincore.loan.domain.model.FundingJournalPostedEvent;
import com.fincore.loan.domain.port.LoanAuditRepository;
import com.fincore.loan.domain.port.LoanFundingRepository;
import com.fincore.loan.domain.port.LoanProcessedEventRepository;

import org.junit.jupiter.api.Test;

class FundingJournalPostedEventHandlerTest {

    @Test
    void acknowledgesJournalOnceAndIsSafeOnDuplicateDelivery() {
        LoanProcessedEventRepository inbox = mock(LoanProcessedEventRepository.class);
        LoanFundingRepository funding = mock(LoanFundingRepository.class);
        LoanAuditRepository audit = mock(LoanAuditRepository.class);
        RepaymentScheduleService repaymentScheduleService = mock(RepaymentScheduleService.class);
        when(inbox.claim(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("FundingJournalPosted")))
                .thenReturn(true, false);
        when(funding.markLedgerPosted(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);
        FundingJournalPostedEventHandler handler = new FundingJournalPostedEventHandler(
                inbox, funding, audit, repaymentScheduleService);
        FundingJournalPostedEvent event = new FundingJournalPostedEvent(
                UUID.randomUUID(), UUID.randomUUID(), 2, "LOAN-001", Instant.now(), UUID.randomUUID(),
                UUID.randomUUID(), new BigDecimal("100.0000"), "IDR");

        assertThat(handler.handle(event)).isEqualTo(LoanEventHandlingResult.PROCESSED);
        assertThat(handler.handle(event)).isEqualTo(LoanEventHandlingResult.DUPLICATE);
        org.mockito.Mockito.verify(funding).markLedgerPosted(
                org.mockito.ArgumentMatchers.eq(event.aggregateId()), org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(audit).append(org.mockito.ArgumentMatchers.any());
    }
}

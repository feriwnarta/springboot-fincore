package com.fincore.ledger.application.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fincore.ledger.domain.enums.JournalSide;
import com.fincore.ledger.domain.enums.JournalStatus;
import com.fincore.ledger.domain.model.JournalEntry;
import com.fincore.ledger.domain.model.JournalLine;
import com.fincore.ledger.domain.model.LedgerOutboxEntry;
import com.fincore.ledger.domain.model.RepaymentConfirmedEvent;
import com.fincore.ledger.domain.port.JournalRepository;
import com.fincore.ledger.domain.port.LedgerAuditRepository;
import com.fincore.ledger.domain.port.LedgerOutboxRepository;
import com.fincore.platform.audit.AuditEvent;
import com.fincore.platform.web.ConflictException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RepaymentJournalPostingService {

    private static final String DEBIT_ACCOUNT = "SIMULATED_SETTLEMENT_CLEARING";
    private static final String PRINCIPAL_ACCOUNT = "LOAN_RECEIVABLE";
    private static final String INTEREST_ACCOUNT = "INTEREST_INCOME";

    private final JournalRepository journalRepository;
    private final LedgerAuditRepository auditRepository;
    private final LedgerOutboxRepository outboxRepository;

    @Transactional
    public JournalEntry post(RepaymentConfirmedEvent event) {
        JournalEntry requested = requestedJournal(event);
        var existing = journalRepository.findByBusinessTransactionReference(event.businessReference());
        if (existing.isPresent()) {
            if (!sameJournal(existing.get(), requested)) {
                throw new ConflictException("repayment business reference was already used for another journal");
            }
            return existing.get();
        }
        journalRepository.save(requested);
        Instant now = Instant.now();
        auditRepository.append(new AuditEvent(
                UUID.randomUUID(), "JournalEntry", requested.id().toString(), 1,
                "REPAYMENT_JOURNAL_POSTED", "SUCCESS", "loan-service", "event-consumer",
                event.correlationId().toString(), event.eventId().toString(),
                event.businessReference(), Map.of("source", "ledger-service", "repaymentId", event.repaymentId()), now));
        UUID eventId = UUID.randomUUID();
        outboxRepository.append(new LedgerOutboxEntry(
                eventId, event.aggregateId(), event.aggregateVersion(), "RepaymentJournalPosted", 1,
                event.businessReference(), payload(eventId, requested, event, now), now));
        return requested;
    }

    private static JournalEntry requestedJournal(RepaymentConfirmedEvent event) {
        List<JournalLine> lines = new ArrayList<>();
        lines.add(new JournalLine(UUID.randomUUID(), DEBIT_ACCOUNT, JournalSide.DEBIT,
                event.amount(), event.currency()));
        if (event.principalAmount().signum() > 0) {
            lines.add(new JournalLine(UUID.randomUUID(), PRINCIPAL_ACCOUNT, JournalSide.CREDIT,
                    event.principalAmount(), event.currency()));
        }
        if (event.interestAmount().signum() > 0) {
            lines.add(new JournalLine(UUID.randomUUID(), INTEREST_ACCOUNT, JournalSide.CREDIT,
                    event.interestAmount(), event.currency()));
        }
        return new JournalEntry(UUID.randomUUID(), event.businessReference(), JournalStatus.POSTED,
                event.currency(), lines);
    }

    private static boolean sameJournal(JournalEntry first, JournalEntry second) {
        return first.currency().equals(second.currency())
                && first.lines().size() == second.lines().size()
                && first.lines().stream().allMatch(firstLine -> second.lines().stream().anyMatch(secondLine ->
                        firstLine.accountCode().equals(secondLine.accountCode())
                                && firstLine.side() == secondLine.side()
                                && firstLine.amount().compareTo(secondLine.amount()) == 0));
    }

    private static String payload(
            UUID eventId, JournalEntry journal, RepaymentConfirmedEvent event, Instant occurredAt) {
        return "{" + "\"eventId\":\"" + eventId + "\",\"eventType\":\"RepaymentJournalPosted\","
                + "\"schemaVersion\":1,\"aggregateId\":\"" + event.aggregateId()
                + "\",\"aggregateVersion\":" + event.aggregateVersion()
                + ",\"occurredAt\":\"" + occurredAt + "\",\"correlationId\":\""
                + event.correlationId() + "\",\"businessReference\":\"" + event.businessReference()
                + "\",\"payload\":{\"journalId\":\"" + journal.id() + "\",\"repaymentId\":\""
                + event.repaymentId() + "\",\"amount\":\"" + event.amount().toPlainString()
                + "\",\"currency\":\"" + event.currency() + "\"}}";
    }
}

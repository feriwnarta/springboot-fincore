package com.fincore.ledger.application.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fincore.ledger.application.command.PostJournalCommand;
import com.fincore.ledger.domain.enums.JournalSide;
import com.fincore.ledger.domain.enums.JournalStatus;
import com.fincore.ledger.domain.model.JournalEntry;
import com.fincore.ledger.domain.model.JournalLine;
import com.fincore.ledger.domain.model.LedgerOutboxEntry;
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
public class JournalPostingService {

    private static final String EVENT_TYPE = "FundingJournalPosted";

    private final JournalRepository journalRepository;
    private final LedgerAuditRepository auditRepository;
    private final LedgerOutboxRepository outboxRepository;

    @Transactional
    public JournalEntry post(PostJournalCommand command) {
        JournalEntry requested = requestedJournal(command);
        var existing = journalRepository.findByBusinessTransactionReference(
                command.businessTransactionReference());
        if (existing.isPresent()) {
            if (!sameJournal(existing.get(), requested)) {
                throw new ConflictException("business transaction reference was already used for another journal");
            }
            return existing.get();
        }

        journalRepository.save(requested);
        Instant now = Instant.now();
        auditRepository.append(new AuditEvent(
                UUID.randomUUID(),
                "JournalEntry",
                requested.id().toString(),
                1,
                "POST",
                "SUCCESS",
                command.actorId(),
                command.actorRole(),
                command.correlationId(),
                command.requestId(),
                command.businessTransactionReference(),
                Map.of("source", "ledger-service", "schemaVersion", 1),
                now));
        UUID eventId = UUID.randomUUID();
        outboxRepository.append(new LedgerOutboxEntry(
                eventId,
                command.aggregateId() == null ? requested.id() : command.aggregateId(),
                1,
                EVENT_TYPE,
                1,
                requested.businessTransactionReference(),
                payload(eventId, requested, command, now),
                now));
        return requested;
    }

    private static JournalEntry requestedJournal(PostJournalCommand command) {
        UUID journalId = UUID.randomUUID();
        return new JournalEntry(
                journalId,
                command.businessTransactionReference(),
                JournalStatus.POSTED,
                command.currency(),
                List.of(
                        new JournalLine(UUID.randomUUID(), command.debitAccountCode(), JournalSide.DEBIT,
                                command.amount(), command.currency()),
                        new JournalLine(UUID.randomUUID(), command.creditAccountCode(), JournalSide.CREDIT,
                                command.amount(), command.currency())));
    }

    private static boolean sameJournal(JournalEntry first, JournalEntry second) {
        return first.currency().equals(second.currency())
                && first.lines().size() == second.lines().size()
                && first.lines().stream().allMatch(firstLine -> second.lines().stream()
                        .anyMatch(secondLine -> firstLine.accountCode().equals(secondLine.accountCode())
                                && firstLine.side() == secondLine.side()
                                && firstLine.amount().compareTo(secondLine.amount()) == 0
                                && firstLine.currency().equals(secondLine.currency())));
    }

    private static String payload(UUID eventId, JournalEntry journal, PostJournalCommand command, Instant occurredAt) {
        JournalLine debit = journal.lines().stream()
                .filter(line -> line.side() == JournalSide.DEBIT)
                .findFirst()
                .orElseThrow();
        JournalLine credit = journal.lines().stream()
                .filter(line -> line.side() == JournalSide.CREDIT)
                .findFirst()
                .orElseThrow();
        UUID correlationId;
        try {
            correlationId = UUID.fromString(command.correlationId());
        } catch (IllegalArgumentException exception) {
            correlationId = UUID.nameUUIDFromBytes(command.correlationId().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return "{"
                + "\"eventId\":\"" + eventId + "\","
                + "\"eventType\":\"FundingJournalPosted\","
                + "\"schemaVersion\":1,"
                + "\"aggregateId\":\"" + (command.aggregateId() == null ? journal.id() : command.aggregateId()) + "\","
                + "\"aggregateVersion\":1,"
                + "\"occurredAt\":\"" + occurredAt + "\","
                + "\"correlationId\":\"" + correlationId + "\","
                + "\"businessReference\":\"" + escape(journal.businessTransactionReference()) + "\","
                + "\"payload\":{"
                + "\"journalId\":\"" + journal.id() + "\","
                + "\"currency\":\"" + journal.currency() + "\","
                + "\"debitAccount\":\"" + escape(debit.accountCode()) + "\","
                + "\"creditAccount\":\"" + escape(credit.accountCode()) + "\","
                + "\"amount\":\"" + debit.amount().toPlainString() + "\"}"
                + "}";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

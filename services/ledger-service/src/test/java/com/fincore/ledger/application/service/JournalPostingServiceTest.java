package com.fincore.ledger.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fincore.ledger.application.command.PostJournalCommand;
import com.fincore.ledger.domain.model.JournalEntry;
import com.fincore.ledger.domain.port.JournalRepository;
import com.fincore.ledger.domain.port.LedgerAuditRepository;
import com.fincore.ledger.domain.port.LedgerOutboxRepository;
import com.fincore.ledger.domain.model.LedgerOutboxEntry;
import com.fincore.platform.audit.AuditEvent;
import com.fincore.platform.web.ConflictException;

import org.junit.jupiter.api.Test;

class JournalPostingServiceTest {

    @Test
    void postsJournalAndRecordsAuditAndOutbox() {
        InMemoryJournalRepository journals = new InMemoryJournalRepository();
        RecordingAuditRepository audits = new RecordingAuditRepository();
        RecordingOutboxRepository outbox = new RecordingOutboxRepository();
        JournalPostingService service = new JournalPostingService(journals, audits, outbox);

        JournalEntry result = service.post(command("FUNDING-001", "100000.0000"));

        assertEquals("FUNDING-001", result.businessTransactionReference());
        assertEquals(1, journals.saved.size());
        assertEquals(1, audits.saved.size());
        assertEquals(1, outbox.saved.size());
        assertEquals("FundingJournalPosted", outbox.saved.getFirst().eventType());
    }

    @Test
    void replaysSameBusinessReferenceWithoutDuplicatingJournal() {
        InMemoryJournalRepository journals = new InMemoryJournalRepository();
        RecordingAuditRepository audits = new RecordingAuditRepository();
        RecordingOutboxRepository outbox = new RecordingOutboxRepository();
        JournalPostingService service = new JournalPostingService(journals, audits, outbox);
        PostJournalCommand command = command("FUNDING-002", "100000.0000");

        JournalEntry first = service.post(command);
        JournalEntry replay = service.post(command);

        assertEquals(first.id(), replay.id());
        assertEquals(1, journals.saved.size());
        assertEquals(1, audits.saved.size());
        assertEquals(1, outbox.saved.size());
    }

    @Test
    void rejectsSameBusinessReferenceWithDifferentJournal() {
        InMemoryJournalRepository journals = new InMemoryJournalRepository();
        JournalPostingService service = new JournalPostingService(
                journals, new RecordingAuditRepository(), new RecordingOutboxRepository());

        service.post(command("FUNDING-003", "100000.0000"));

        assertThrows(ConflictException.class,
                () -> service.post(command("FUNDING-003", "200000.0000")));
    }

    private static PostJournalCommand command(String reference, String amount) {
        return new PostJournalCommand(
                reference,
                "IDR",
                "LOAN_RECEIVABLE",
                "SIMULATED_SETTLEMENT_CLEARING",
                new BigDecimal(amount),
                "system",
                "operator",
                "correlation-001",
                "request-001");
    }

    private static final class InMemoryJournalRepository implements JournalRepository {
        private final List<JournalEntry> saved = new ArrayList<>();

        @Override
        public Optional<JournalEntry> findByBusinessTransactionReference(String reference) {
            return saved.stream()
                    .filter(journal -> journal.businessTransactionReference().equals(reference))
                    .findFirst();
        }

        @Override
        public void save(JournalEntry journal) {
            saved.add(journal);
        }
    }

    private static final class RecordingAuditRepository implements LedgerAuditRepository {
        private final List<AuditEvent> saved = new ArrayList<>();

        @Override
        public void append(AuditEvent event) {
            saved.add(event);
        }
    }

    private static final class RecordingOutboxRepository implements LedgerOutboxRepository {
        private final List<LedgerOutboxEntry> saved = new ArrayList<>();

        @Override
        public void append(LedgerOutboxEntry event) {
            saved.add(event);
        }
    }
}

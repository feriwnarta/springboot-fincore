package com.fincore.ledger.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.fincore.ledger.domain.model.JournalEntry;
import com.fincore.ledger.domain.model.LedgerOutboxEntry;
import com.fincore.ledger.domain.port.JournalRepository;
import com.fincore.ledger.domain.port.LedgerAuditRepository;
import com.fincore.ledger.domain.port.LedgerOutboxRepository;
import com.fincore.ledger.domain.port.ProcessedEventRepository;
import com.fincore.ledger.domain.model.LoanFundedEvent;
import com.fincore.platform.audit.AuditEvent;

import org.junit.jupiter.api.Test;

class LoanFundedEventHandlerTest {

    @Test
    void postsFundingJournalAndClaimsEventOnce() {
        InMemoryJournalRepository journals = new InMemoryJournalRepository();
        InMemoryProcessedEventRepository processedEvents = new InMemoryProcessedEventRepository();
        RecordingAuditRepository audits = new RecordingAuditRepository();
        RecordingOutboxRepository outbox = new RecordingOutboxRepository();
        LoanFundedEventHandler handler = handler(journals, processedEvents, audits, outbox);
        LoanFundedEvent event = event();

        EventHandlingResult first = handler.handle(event);
        EventHandlingResult replay = handler.handle(event);

        assertEquals(EventHandlingResult.PROCESSED, first);
        assertEquals(EventHandlingResult.DUPLICATE, replay);
        assertEquals(1, processedEvents.claimed.size());
        assertEquals(1, journals.saved.size());
        assertEquals(1, audits.saved.size());
        assertEquals(1, outbox.saved.size());
    }

    private static LoanFundedEventHandler handler(
            InMemoryJournalRepository journals,
            InMemoryProcessedEventRepository processedEvents,
            RecordingAuditRepository audits,
            RecordingOutboxRepository outbox) {
        return new LoanFundedEventHandler(
                processedEvents,
                new JournalPostingService(journals, audits, outbox));
    }

    private static LoanFundedEvent event() {
        return new LoanFundedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                4,
                "LOAN-FUNDED-001",
                Instant.parse("2026-11-17T03:00:00Z"),
                UUID.randomUUID(),
                "customer-001",
                "STANDARD",
                new BigDecimal("15000000.0000"),
                "IDR");
    }

    private static final class InMemoryProcessedEventRepository implements ProcessedEventRepository {
        private final List<UUID> claimed = new ArrayList<>();
        private final Set<UUID> seen = new HashSet<>();

        @Override
        public boolean claim(UUID eventId, String eventType) {
            if (!seen.add(eventId)) {
                return false;
            }
            claimed.add(eventId);
            return true;
        }
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

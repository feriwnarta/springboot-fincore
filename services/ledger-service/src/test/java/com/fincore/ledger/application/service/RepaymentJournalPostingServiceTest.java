package com.fincore.ledger.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fincore.ledger.domain.model.JournalEntry;
import com.fincore.ledger.domain.model.LedgerOutboxEntry;
import com.fincore.ledger.domain.model.RepaymentConfirmedEvent;
import com.fincore.ledger.domain.port.JournalRepository;
import com.fincore.ledger.domain.port.LedgerAuditRepository;
import com.fincore.ledger.domain.port.LedgerOutboxRepository;
import com.fincore.platform.audit.AuditEvent;

import org.junit.jupiter.api.Test;

class RepaymentJournalPostingServiceTest {

    @Test
    void postsBalancedThreeLineJournalAndAcknowledgementOutbox() {
        var journals = new InMemoryJournalRepository();
        var audits = new RecordingAuditRepository();
        var outbox = new RecordingOutboxRepository();
        var service = new RepaymentJournalPostingService(journals, audits, outbox);
        var event = new RepaymentConfirmedEvent(
                UUID.randomUUID(), UUID.randomUUID(), 1, "REPAY-1", Instant.now(), UUID.randomUUID(),
                UUID.randomUUID(), money("18"), money("10"), money("8"), "IDR");

        JournalEntry result = service.post(event);

        assertThat(result.lines()).hasSize(3);
        assertThat(result.lines().stream().map(line -> line.amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(money("36"));
        assertThat(audits.saved).hasSize(1);
        assertThat(outbox.saved).singleElement().extracting(LedgerOutboxEntry::eventType)
                .isEqualTo("RepaymentJournalPosted");
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(4);
    }

    private static final class InMemoryJournalRepository implements JournalRepository {
        private final List<JournalEntry> saved = new ArrayList<>();

        @Override
        public Optional<JournalEntry> findByBusinessTransactionReference(String reference) {
            return saved.stream().filter(journal -> journal.businessTransactionReference().equals(reference)).findFirst();
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

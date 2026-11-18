package com.fincore.ledger.domain.port;

import java.util.Optional;

import com.fincore.ledger.domain.model.JournalEntry;

public interface JournalRepository {

    Optional<JournalEntry> findByBusinessTransactionReference(String reference);

    void save(JournalEntry journal);
}

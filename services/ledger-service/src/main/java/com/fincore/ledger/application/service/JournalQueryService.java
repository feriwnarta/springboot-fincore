package com.fincore.ledger.application.service;

import com.fincore.ledger.domain.model.JournalEntry;
import com.fincore.ledger.domain.port.JournalRepository;
import com.fincore.platform.web.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JournalQueryService {

    private final JournalRepository journalRepository;

    public JournalEntry findByBusinessTransactionReference(String reference) {
        return journalRepository.findByBusinessTransactionReference(reference)
                .orElseThrow(() -> new ResourceNotFoundException("Journal was not found"));
    }
}

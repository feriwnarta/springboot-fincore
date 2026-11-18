package com.fincore.ledger.api.mapper;

import java.util.List;

import com.fincore.ledger.api.dto.response.JournalLineResponse;
import com.fincore.ledger.api.dto.response.JournalResponse;
import com.fincore.ledger.domain.model.JournalEntry;

public final class JournalMapper {

    private JournalMapper() {
    }

    public static JournalResponse toResponse(JournalEntry journal) {
        return new JournalResponse(
                journal.id().toString(),
                journal.businessTransactionReference(),
                journal.status().name(),
                journal.currency(),
                journal.lines().stream()
                        .map(line -> new JournalLineResponse(
                                line.accountCode(),
                                line.side().name(),
                                line.amount().toPlainString(),
                                line.currency()))
                        .toList());
    }
}

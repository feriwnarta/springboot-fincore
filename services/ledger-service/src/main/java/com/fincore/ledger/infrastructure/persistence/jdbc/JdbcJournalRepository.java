package com.fincore.ledger.infrastructure.persistence.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fincore.ledger.domain.enums.JournalSide;
import com.fincore.ledger.domain.enums.JournalStatus;
import com.fincore.ledger.domain.model.JournalEntry;
import com.fincore.ledger.domain.model.JournalLine;
import com.fincore.ledger.domain.port.JournalRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcJournalRepository implements JournalRepository {

    private static final RowMapper<JournalEntryRow> ENTRY_ROW_MAPPER = (resultSet, rowNumber) ->
            new JournalEntryRow(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getString("business_tx_ref"),
                    JournalStatus.valueOf(resultSet.getString("status")),
                    resultSet.getString("currency"));

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<JournalEntry> findByBusinessTransactionReference(String reference) {
        List<JournalEntryRow> entries = jdbcTemplate.query(
                "SELECT id, business_tx_ref, status, currency FROM journal_entries "
                        + "WHERE business_tx_ref = ? LIMIT 1",
                ENTRY_ROW_MAPPER,
                reference);
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        JournalEntryRow entry = entries.getFirst();
        List<JournalLine> lines = jdbcTemplate.query(
                "SELECT id, account_code, side, amount, currency FROM journal_lines "
                        + "WHERE entry_id = ? ORDER BY id",
                JdbcJournalRepository::mapLine,
                entry.id());
        return Optional.of(new JournalEntry(
                entry.id(), entry.businessReference(), entry.status(), entry.currency(), lines));
    }

    @Override
    public void save(JournalEntry journal) {
        jdbcTemplate.update(
                "INSERT INTO journal_entries (id, business_tx_ref, status, currency) VALUES (?, ?, ?, ?)",
                journal.id(), journal.businessTransactionReference(), journal.status().name(), journal.currency());
        for (JournalLine line : journal.lines()) {
            jdbcTemplate.update(
                    "INSERT INTO journal_lines (id, entry_id, account_code, side, amount, currency) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    line.id(), journal.id(), line.accountCode(), line.side().name(), line.amount(), line.currency());
        }
    }

    private static JournalLine mapLine(ResultSet resultSet, int rowNumber) throws SQLException {
        return new JournalLine(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("account_code"),
                JournalSide.valueOf(resultSet.getString("side")),
                resultSet.getBigDecimal("amount"),
                resultSet.getString("currency"));
    }

    private record JournalEntryRow(UUID id, String businessReference, JournalStatus status, String currency) {
    }
}

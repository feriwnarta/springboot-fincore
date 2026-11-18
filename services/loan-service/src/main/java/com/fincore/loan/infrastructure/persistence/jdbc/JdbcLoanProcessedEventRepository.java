package com.fincore.loan.infrastructure.persistence.jdbc;

import java.util.UUID;

import com.fincore.loan.domain.port.LoanProcessedEventRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcLoanProcessedEventRepository implements LoanProcessedEventRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public boolean claim(UUID eventId, String eventType) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO loan_processed_events (event_id, event_type) VALUES (?, ?)",
                    eventId, eventType);
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }
}

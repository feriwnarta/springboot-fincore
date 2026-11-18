package com.fincore.ledger.infrastructure.persistence.jdbc;

import java.util.UUID;

import com.fincore.ledger.domain.port.ProcessedEventRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcProcessedEventRepository implements ProcessedEventRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public boolean claim(UUID eventId, String eventType) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO processed_events (event_id, event_type) VALUES (?, ?)",
                    eventId,
                    eventType);
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }
}

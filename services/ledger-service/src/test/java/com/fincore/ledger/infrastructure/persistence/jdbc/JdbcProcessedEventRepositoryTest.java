package com.fincore.ledger.infrastructure.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class JdbcProcessedEventRepositoryTest {

    private final EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .addScript("schema.sql")
            .build();
    private final JdbcProcessedEventRepository repository =
            new JdbcProcessedEventRepository(new JdbcTemplate(database));

    @AfterEach
    void closeDatabase() {
        database.shutdown();
    }

    @Test
    void claimsEventOnlyOnce() {
        UUID eventId = UUID.randomUUID();

        assertTrue(repository.claim(eventId, "LoanFunded"));
        assertFalse(repository.claim(eventId, "LoanFunded"));
    }
}

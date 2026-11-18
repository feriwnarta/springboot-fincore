package com.fincore.loan.infrastructure.persistence.jdbc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import com.fincore.loan.domain.enums.LoanApplicationStatus;
import com.fincore.loan.domain.port.LoanApplicationTransitionRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcLoanApplicationTransitionRepository implements LoanApplicationTransitionRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public boolean transition(UUID id, long expectedVersion, LoanApplicationStatus targetStatus,
            Instant transitionedAt) {
        Timestamp timestamp = Timestamp.from(transitionedAt);
        return jdbcTemplate.update("""
                UPDATE loan_applications
                SET status = ?, version = version + 1,
                    approved_at = CASE WHEN ? = 'APPROVED' THEN ? ELSE approved_at END,
                    rejected_at = CASE WHEN ? = 'REJECTED' THEN ? ELSE rejected_at END
                WHERE id = ? AND version = ? AND status = 'SUBMITTED'
                """,
                targetStatus.name(), targetStatus.name(), timestamp,
                targetStatus.name(), timestamp, id, expectedVersion) == 1;
    }
}

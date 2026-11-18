package com.fincore.loan.infrastructure.persistence.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fincore.loan.domain.enums.LoanApplicationStatus;
import com.fincore.loan.domain.model.LoanApplication;
import com.fincore.loan.domain.port.LoanApplicationRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcLoanApplicationRepository implements LoanApplicationRepository {

    private static final RowMapper<LoanApplication> ROW_MAPPER = JdbcLoanApplicationRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void save(LoanApplication application) {
        jdbcTemplate.update("""
                INSERT INTO loan_applications
                    (id, customer_id, product_code, principal_amount, tenor_months, status,
                     version, created_at, approved_at, rejected_at, actor_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                application.id(), application.customerId(), application.productCode(),
                application.principalAmount(), application.tenorMonths(), application.status().name(),
                application.version(), toTimestamp(application.createdAt()), toTimestamp(application.approvedAt()),
                toTimestamp(application.rejectedAt()), application.actorId());
    }

    @Override
    public Optional<LoanApplication> findById(UUID id) {
        List<LoanApplication> applications = jdbcTemplate.query("""
                SELECT id, customer_id, product_code, principal_amount, tenor_months, status,
                       version, created_at, approved_at, rejected_at, actor_id
                FROM loan_applications
                WHERE id = ?
                """, ROW_MAPPER, id);
        return applications.stream().findFirst();
    }

    @Override
    public List<LoanApplication> findPage(
            String customerId,
            LoanApplicationStatus status,
            String productCode,
            Instant beforeCreatedAt,
            UUID beforeId,
            int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, customer_id, product_code, principal_amount, tenor_months, status,
                       version, created_at, approved_at, rejected_at, actor_id
                FROM loan_applications
                WHERE 1 = 1
                """);
        List<Object> arguments = new ArrayList<>();
        if (customerId != null) {
            sql.append(" AND customer_id = ?");
            arguments.add(customerId);
        }
        if (status != null) {
            sql.append(" AND status = ?");
            arguments.add(status.name());
        }
        if (productCode != null) {
            sql.append(" AND product_code = ?");
            arguments.add(productCode);
        }
        if (beforeCreatedAt != null && beforeId != null) {
            sql.append(" AND (created_at, id) < (?, ?)");
            arguments.add(Timestamp.from(beforeCreatedAt));
            arguments.add(beforeId);
        }
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT ?");
        arguments.add(limit);
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, arguments.toArray());
    }

    private static LoanApplication mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new LoanApplication(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("customer_id"),
                resultSet.getString("product_code"),
                resultSet.getBigDecimal("principal_amount"),
                resultSet.getInt("tenor_months"),
                LoanApplicationStatus.valueOf(resultSet.getString("status")),
                resultSet.getLong("version"),
                resultSet.getTimestamp("created_at").toInstant(),
                timestampToInstant(resultSet, "approved_at"),
                timestampToInstant(resultSet, "rejected_at"),
                resultSet.getString("actor_id"),
                "APP-" + resultSet.getString("id"));
    }

    private static java.time.Instant timestampToInstant(ResultSet resultSet, String column) throws SQLException {
        java.sql.Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Timestamp toTimestamp(java.time.Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}

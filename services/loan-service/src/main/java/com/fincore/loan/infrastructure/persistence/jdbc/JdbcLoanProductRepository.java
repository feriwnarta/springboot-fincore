package com.fincore.loan.infrastructure.persistence.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import com.fincore.loan.domain.enums.InterestType;
import com.fincore.loan.domain.model.LoanProduct;
import com.fincore.loan.domain.port.LoanProductRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcLoanProductRepository implements LoanProductRepository {

    private static final String BASE_QUERY = """
            SELECT id, code, interest_type, annual_rate, min_amount, max_amount,
                   active, version, updated_at
            FROM loan_products
            WHERE active = TRUE
            """;

    private static final RowMapper<LoanProduct> ROW_MAPPER = JdbcLoanProductRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<LoanProduct> findActiveAfter(String codeCursor, int limit) {
        if (codeCursor == null) {
            return jdbcTemplate.query(
                    BASE_QUERY + " ORDER BY code ASC LIMIT ?",
                    ROW_MAPPER,
                    limit);
        }
        return jdbcTemplate.query(
                BASE_QUERY + " AND code > ? ORDER BY code ASC LIMIT ?",
                ROW_MAPPER,
                codeCursor,
                limit);
    }

    @Override
    public Optional<LoanProduct> findActiveByCode(String code) {
        List<LoanProduct> products = jdbcTemplate.query(
                BASE_QUERY + " AND code = ? LIMIT 1",
                ROW_MAPPER,
                code);
        return products.stream().findFirst();
    }

    @Override
    public Optional<LoanProduct> findByCode(String code) {
        List<LoanProduct> products = jdbcTemplate.query("""
                SELECT id, code, interest_type, annual_rate, min_amount, max_amount,
                       active, version, updated_at
                FROM loan_products
                WHERE code = ?
                LIMIT 1
                """, ROW_MAPPER, code);
        return products.stream().findFirst();
    }

    @Override
    public void save(LoanProduct product) {
        jdbcTemplate.update("""
                INSERT INTO loan_products
                    (id, code, interest_type, annual_rate, min_amount, max_amount,
                     active, version, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                product.id(), product.code(), product.interestType().name(), product.annualRate(),
                product.minAmount(), product.maxAmount(), product.active(), product.version(),
                Timestamp.from(product.updatedAt()));
    }

    private static LoanProduct mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new LoanProduct(
                resultSet.getObject("id", java.util.UUID.class),
                resultSet.getString("code"),
                InterestType.valueOf(resultSet.getString("interest_type")),
                resultSet.getBigDecimal("annual_rate"),
                resultSet.getBigDecimal("min_amount"),
                resultSet.getBigDecimal("max_amount"),
                resultSet.getBoolean("active"),
                resultSet.getLong("version"),
                resultSet.getTimestamp("updated_at").toInstant());
    }
}

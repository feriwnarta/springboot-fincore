package com.fincore.ledger.infrastructure.persistence.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fincore.ledger.domain.enums.SavingsAccountStatus;
import com.fincore.ledger.domain.enums.SavingsOperationType;
import com.fincore.ledger.domain.model.SavingsAccount;
import com.fincore.ledger.domain.model.SavingsTransaction;
import com.fincore.ledger.domain.port.SavingsRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcSavingsRepository implements SavingsRepository {

    private static final RowMapper<SavingsAccount> ACCOUNT_MAPPER = JdbcSavingsRepository::mapAccount;
    private static final RowMapper<SavingsTransaction> TRANSACTION_MAPPER = JdbcSavingsRepository::mapTransaction;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void saveAccount(SavingsAccount account) {
        jdbcTemplate.update("""
                INSERT INTO savings_accounts
                    (id, account_no, customer_id, currency, status, current_balance, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, account.id(), account.accountNumber(), account.customerId(), account.currency(),
                account.status().name(), account.currentBalance(), account.version(),
                Timestamp.from(account.createdAt()), Timestamp.from(account.updatedAt()));
    }

    @Override
    public Optional<SavingsAccount> findAccountByIdForUpdate(UUID accountId) {
        return queryAccount("SELECT id, account_no, customer_id, currency, status, current_balance, version, "
                + "created_at, updated_at FROM savings_accounts WHERE id = ? FOR UPDATE", accountId);
    }

    @Override
    public Optional<SavingsAccount> findAccountById(UUID accountId) {
        return queryAccount("SELECT id, account_no, customer_id, currency, status, current_balance, version, "
                + "created_at, updated_at FROM savings_accounts WHERE id = ?", accountId);
    }

    @Override
    public boolean updateBalance(UUID accountId, long expectedVersion, java.math.BigDecimal nextBalance) {
        return jdbcTemplate.update("""
                UPDATE savings_accounts
                SET current_balance = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND version = ? AND status = 'ACTIVE'
                """, nextBalance, accountId, expectedVersion) == 1;
    }

    @Override
    public boolean updateStatus(UUID accountId, SavingsAccountStatus status) {
        return jdbcTemplate.update("""
                UPDATE savings_accounts SET status = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status <> 'CLOSED'
                """, status.name(), accountId) == 1;
    }

    @Override
    public void saveTransaction(SavingsTransaction transaction) {
        jdbcTemplate.update("""
                INSERT INTO savings_transactions
                    (id, account_id, business_ref, operation_type, amount, balance_after, journal_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, transaction.id(), transaction.accountId(), transaction.businessReference(),
                transaction.operationType().name(), transaction.amount(), transaction.balanceAfter(),
                transaction.journalId(), Timestamp.from(transaction.createdAt()));
    }

    @Override
    public Optional<SavingsTransaction> findTransactionByBusinessReference(String businessReference) {
        List<SavingsTransaction> transactions = jdbcTemplate.query("""
                SELECT id, account_id, business_ref, operation_type, amount, balance_after, journal_id, created_at
                FROM savings_transactions WHERE business_ref = ?
                """, TRANSACTION_MAPPER, businessReference);
        return transactions.stream().findFirst();
    }

    @Override
    public List<SavingsTransaction> findTransactions(UUID accountId, String cursor, int limit) {
        if (cursor == null || cursor.isBlank()) {
            return jdbcTemplate.query("""
                    SELECT id, account_id, business_ref, operation_type, amount, balance_after, journal_id, created_at
                    FROM savings_transactions WHERE account_id = ?
                    ORDER BY created_at DESC, id DESC LIMIT ?
                    """, TRANSACTION_MAPPER, accountId, limit);
        }
        String[] parts = cursor.split("\\|", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("cursor must be createdAt|id");
        }
        Instant createdAt = Instant.parse(parts[0]);
        UUID id = UUID.fromString(parts[1]);
        return jdbcTemplate.query("""
                SELECT id, account_id, business_ref, operation_type, amount, balance_after, journal_id, created_at
                FROM savings_transactions
                WHERE account_id = ? AND (created_at, id) < (?, ?)
                ORDER BY created_at DESC, id DESC LIMIT ?
                """, TRANSACTION_MAPPER, accountId, Timestamp.from(createdAt), id, limit);
    }

    private Optional<SavingsAccount> queryAccount(String sql, UUID accountId) {
        List<SavingsAccount> accounts = jdbcTemplate.query(sql, ACCOUNT_MAPPER, accountId);
        return accounts.stream().findFirst();
    }

    private static SavingsAccount mapAccount(ResultSet resultSet, int rowNumber) throws SQLException {
        return new SavingsAccount(
                resultSet.getObject("id", UUID.class), resultSet.getString("account_no"),
                resultSet.getString("customer_id"), resultSet.getString("currency"),
                SavingsAccountStatus.valueOf(resultSet.getString("status")),
                resultSet.getBigDecimal("current_balance"), resultSet.getLong("version"),
                resultSet.getTimestamp("created_at").toInstant(), resultSet.getTimestamp("updated_at").toInstant());
    }

    private static SavingsTransaction mapTransaction(ResultSet resultSet, int rowNumber) throws SQLException {
        return new SavingsTransaction(
                resultSet.getObject("id", UUID.class), resultSet.getObject("account_id", UUID.class),
                resultSet.getString("business_ref"), SavingsOperationType.valueOf(resultSet.getString("operation_type")),
                resultSet.getBigDecimal("amount"), resultSet.getBigDecimal("balance_after"),
                resultSet.getObject("journal_id", UUID.class), resultSet.getTimestamp("created_at").toInstant());
    }
}

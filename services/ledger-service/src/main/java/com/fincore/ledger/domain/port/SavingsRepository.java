package com.fincore.ledger.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fincore.ledger.domain.model.SavingsAccount;
import com.fincore.ledger.domain.model.SavingsTransaction;
import com.fincore.ledger.domain.enums.SavingsAccountStatus;

public interface SavingsRepository {

    void saveAccount(SavingsAccount account);

    Optional<SavingsAccount> findAccountByIdForUpdate(UUID accountId);

    Optional<SavingsAccount> findAccountById(UUID accountId);

    boolean updateBalance(UUID accountId, long expectedVersion, java.math.BigDecimal nextBalance);

    boolean updateStatus(UUID accountId, SavingsAccountStatus status);

    void saveTransaction(SavingsTransaction transaction);

    Optional<SavingsTransaction> findTransactionByBusinessReference(String businessReference);

    List<SavingsTransaction> findTransactions(UUID accountId, String cursor, int limit);
}

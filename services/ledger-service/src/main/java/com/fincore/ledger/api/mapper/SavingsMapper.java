package com.fincore.ledger.api.mapper;

import com.fincore.ledger.api.dto.response.SavingsAccountResponse;
import com.fincore.ledger.api.dto.response.SavingsTransactionResponse;
import com.fincore.ledger.domain.model.SavingsAccount;
import com.fincore.ledger.domain.model.SavingsTransaction;

public final class SavingsMapper {

    private SavingsMapper() {
    }

    public static SavingsAccountResponse toResponse(SavingsAccount account) {
        return new SavingsAccountResponse(account.id().toString(), account.accountNumber(), account.customerId(),
                account.currency(), account.status().name(), account.currentBalance().toPlainString(), account.version(),
                account.createdAt(), account.updatedAt());
    }

    public static SavingsTransactionResponse toResponse(SavingsTransaction transaction) {
        return new SavingsTransactionResponse(transaction.id().toString(), transaction.accountId().toString(),
                transaction.businessReference(), transaction.operationType().name(), transaction.amount().toPlainString(),
                transaction.balanceAfter().toPlainString(), transaction.journalId().toString(), transaction.createdAt());
    }
}

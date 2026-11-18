package com.fincore.ledger.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fincore.ledger.domain.enums.SavingsAccountStatus;

public record SavingsAccount(
        UUID id,
        String accountNumber,
        String customerId,
        String currency,
        SavingsAccountStatus status,
        BigDecimal currentBalance,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public SavingsAccount {
        if (id == null || accountNumber == null || accountNumber.isBlank()
                || customerId == null || customerId.isBlank() || currency == null || currency.length() != 3
                || status == null || currentBalance == null || currentBalance.signum() < 0 || version < 1) {
            throw new IllegalArgumentException("invalid savings account");
        }
    }
}

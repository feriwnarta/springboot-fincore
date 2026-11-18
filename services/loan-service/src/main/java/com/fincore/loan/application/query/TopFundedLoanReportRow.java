package com.fincore.loan.application.query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TopFundedLoanReportRow(
        UUID loanId,
        String customerId,
        BigDecimal principalAmount,
        Instant fundedAt,
        int rank) {
}

package com.fincore.loan.application.query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RunningFundedTotalReportRow(
        UUID loanId,
        String productCode,
        Instant fundedAt,
        BigDecimal principalAmount,
        BigDecimal runningPrincipal) {
}

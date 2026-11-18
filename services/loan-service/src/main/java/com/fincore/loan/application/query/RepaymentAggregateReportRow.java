package com.fincore.loan.application.query;

import java.math.BigDecimal;
import java.util.UUID;

public record RepaymentAggregateReportRow(
        UUID loanId,
        BigDecimal postedPrincipal,
        BigDecimal postedInterest,
        BigDecimal postedTotal) {
}

package com.fincore.loan.application.query;

import java.math.BigDecimal;
import java.util.UUID;

public record TopRepaidLoanReportRow(UUID loanId, BigDecimal completedRepaymentAmount) {
}

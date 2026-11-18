package com.fincore.loan.api.dto.response;

import java.util.UUID;

public record TopFundedLoanReportResponse(
        UUID loanId,
        String customerId,
        String principalAmount,
        String fundedAt,
        int rank) {
}

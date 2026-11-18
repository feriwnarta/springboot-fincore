package com.fincore.loan.api.dto.response;

import java.util.UUID;

public record RunningFundedTotalReportResponse(
        UUID loanId,
        String productCode,
        String fundedAt,
        String principalAmount,
        String runningPrincipal) {
}

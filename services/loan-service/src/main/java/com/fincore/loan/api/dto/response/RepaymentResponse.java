package com.fincore.loan.api.dto.response;

import java.time.Instant;

public record RepaymentResponse(
        String repaymentId,
        String loanId,
        String businessReference,
        String amount,
        String status,
        String simulatedReceiptReference,
        String ledgerReference,
        Instant createdAt,
        Instant postedAt) {
}

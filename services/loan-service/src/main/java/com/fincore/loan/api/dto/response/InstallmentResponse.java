package com.fincore.loan.api.dto.response;

public record InstallmentResponse(
        int installmentNo,
        String principalAmount,
        String interestAmount,
        String totalAmount) {
}

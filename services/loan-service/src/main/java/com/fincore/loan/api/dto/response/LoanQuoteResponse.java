package com.fincore.loan.api.dto.response;

import java.util.List;

public record LoanQuoteResponse(
        String productCode,
        String interestType,
        String annualRate,
        String principalAmount,
        int tenorMonths,
        String totalInterest,
        String totalRepayment,
        String monthlyInstallment,
        List<InstallmentResponse> installments) {
}

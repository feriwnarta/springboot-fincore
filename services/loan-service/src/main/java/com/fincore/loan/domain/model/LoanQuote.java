package com.fincore.loan.domain.model;

import java.math.BigDecimal;
import java.util.List;

import com.fincore.loan.domain.enums.InterestType;
import com.fincore.loan.domain.service.pricing.Installment;

public record LoanQuote(
        String productCode,
        InterestType interestType,
        BigDecimal annualRate,
        BigDecimal principalAmount,
        int tenorMonths,
        BigDecimal totalInterest,
        BigDecimal totalRepayment,
        BigDecimal monthlyInstallment,
        List<Installment> installments) {

    public LoanQuote {
        installments = installments == null ? List.of() : List.copyOf(installments);
    }
}

package com.fincore.loan.domain.service.pricing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public record PricingResult(
        BigDecimal totalInterest,
        BigDecimal totalRepayment,
        BigDecimal monthlyInstallment,
        List<Installment> installments) {

    public PricingResult {
        Objects.requireNonNull(totalInterest, "totalInterest must not be null");
        Objects.requireNonNull(totalRepayment, "totalRepayment must not be null");
        Objects.requireNonNull(monthlyInstallment, "monthlyInstallment must not be null");
        installments = installments == null ? List.of() : List.copyOf(installments);
    }
}

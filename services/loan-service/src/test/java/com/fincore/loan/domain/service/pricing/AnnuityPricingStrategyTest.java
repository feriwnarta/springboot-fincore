package com.fincore.loan.domain.service.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class AnnuityPricingStrategyTest {

    private final AnnuityPricingStrategy strategy = new AnnuityPricingStrategy();

    @Test
    void calculatesAnnuityInstallmentsUsingMonthlyRate() {
        PricingResult result = strategy.calculate(new PricingRequest(
                new BigDecimal("1000000.0000"),
                new BigDecimal("0.120000"),
                12));

        assertThat(result.monthlyInstallment()).isEqualByComparingTo("88848.7887");
        assertThat(result.installments()).hasSize(12);
        assertThat(result.totalRepayment()).isEqualByComparingTo(
                result.installments().stream()
                        .map(Installment::totalAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
        assertThat(result.totalInterest()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void handlesZeroRateWithoutDividingByZero() {
        PricingResult result = strategy.calculate(new PricingRequest(
                new BigDecimal("1000000.0000"),
                BigDecimal.ZERO,
                4));

        assertThat(result.totalInterest()).isEqualByComparingTo("0.0000");
        assertThat(result.monthlyInstallment()).isEqualByComparingTo("250000.0000");
    }
}

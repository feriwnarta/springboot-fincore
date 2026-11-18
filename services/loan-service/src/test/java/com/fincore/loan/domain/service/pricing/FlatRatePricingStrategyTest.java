package com.fincore.loan.domain.service.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class FlatRatePricingStrategyTest {

    private final FlatRatePricingStrategy strategy = new FlatRatePricingStrategy();

    @Test
    void calculatesFlatInterestAndKeepsTheScheduleTotalBalanced() {
        PricingResult result = strategy.calculate(new PricingRequest(
                new BigDecimal("1000000.0000"),
                new BigDecimal("0.120000"),
                12));

        assertThat(result.totalInterest()).isEqualByComparingTo("120000.0000");
        assertThat(result.totalRepayment()).isEqualByComparingTo("1120000.0000");
        assertThat(result.monthlyInstallment()).isEqualByComparingTo("93333.3333");
        assertThat(result.installments()).hasSize(12);
        assertThat(result.installments().stream()
                .map(Installment::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(result.totalRepayment());
        assertThat(result.installments().getLast().totalAmount())
                .isEqualByComparingTo("93333.3337");
    }
}

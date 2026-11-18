package com.fincore.loan.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fincore.loan.domain.enums.InterestType;
import com.fincore.loan.domain.model.LoanProduct;
import com.fincore.loan.domain.service.pricing.AnnuityPricingStrategy;
import com.fincore.loan.domain.service.pricing.FlatRatePricingStrategy;

import org.junit.jupiter.api.Test;

class RepaymentScheduleGeneratorTest {

    private final RepaymentScheduleGenerator generator = new RepaymentScheduleGenerator(
            List.of(new FlatRatePricingStrategy(), new AnnuityPricingStrategy()));

    @Test
    void generatesFlatScheduleWithPrincipalAndInterestRoundingOnFinalPeriod() {
        var schedules = generator.generate(product(InterestType.FLAT), money("100"), 3,
                LocalDate.of(2026, 1, 31));

        assertThat(schedules).hasSize(3);
        assertThat(schedules.get(0).dueDate()).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(schedules.get(1).dueDate()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(schedules).extracting(schedule -> schedule.principalDue())
                .containsExactly(money("33.3333"), money("33.3333"), money("33.3334"));
        assertThat(schedules.stream().map(schedule -> schedule.principalDue())
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(money("100"));
    }

    @Test
    void generatesAnnuityScheduleWithDecreasingInterest() {
        var schedules = generator.generate(product(InterestType.ANNUITY), money("1200"), 3,
                LocalDate.of(2026, 1, 1));

        assertThat(schedules).hasSize(3);
        assertThat(schedules.get(0).interestDue()).isGreaterThan(schedules.get(1).interestDue());
        assertThat(schedules.get(1).interestDue()).isGreaterThan(schedules.get(2).interestDue());
        assertThat(schedules.stream().map(schedule -> schedule.principalDue())
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(money("1200"));
    }

    private static LoanProduct product(InterestType interestType) {
        return new LoanProduct(UUID.randomUUID(), "P-" + interestType, interestType,
                new BigDecimal("0.12"), money("1"), money("100000"), true, 1, Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(4);
    }
}

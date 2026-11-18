package com.fincore.loan.domain.service;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fincore.loan.domain.enums.InterestType;
import com.fincore.loan.domain.model.LoanProduct;
import com.fincore.loan.domain.model.RepaymentScheduleDraft;
import com.fincore.loan.domain.service.pricing.Installment;
import com.fincore.loan.domain.service.pricing.PricingRequest;
import com.fincore.loan.domain.service.pricing.PricingStrategy;

public class RepaymentScheduleGenerator {

    private final Map<InterestType, PricingStrategy> strategies;

    public RepaymentScheduleGenerator(List<PricingStrategy> pricingStrategies) {
        Objects.requireNonNull(pricingStrategies, "pricingStrategies must not be null");
        EnumMap<InterestType, PricingStrategy> resolved = new EnumMap<>(InterestType.class);
        for (PricingStrategy strategy : pricingStrategies) {
            if (resolved.put(strategy.interestType(), strategy) != null) {
                throw new IllegalArgumentException("Duplicate pricing strategy for " + strategy.interestType());
            }
        }
        this.strategies = Map.copyOf(resolved);
    }

    public List<RepaymentScheduleDraft> generate(
            LoanProduct product, java.math.BigDecimal principalAmount, int tenorMonths, LocalDate firstDueDate) {
        Objects.requireNonNull(product, "product must not be null");
        Objects.requireNonNull(principalAmount, "principalAmount must not be null");
        Objects.requireNonNull(firstDueDate, "firstDueDate must not be null");
        PricingStrategy strategy = strategies.get(product.interestType());
        if (strategy == null) {
            throw new IllegalArgumentException("No pricing strategy configured for " + product.interestType());
        }
        List<Installment> installments = strategy.calculate(
                new PricingRequest(principalAmount, product.annualRate(), tenorMonths)).installments();
        return installments.stream()
                .map(installment -> toDraft(installment, firstDueDate))
                .toList();
    }

    private static RepaymentScheduleDraft toDraft(Installment installment, LocalDate firstDueDate) {
        return new RepaymentScheduleDraft(
                installment.installmentNo(),
                firstDueDate.plusMonths(installment.installmentNo() - 1L),
                installment.principalAmount(),
                installment.interestAmount());
    }
}

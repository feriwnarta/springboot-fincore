package com.fincore.loan.domain.service.pricing;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import com.fincore.loan.domain.enums.InterestType;

public class FlatRatePricingStrategy implements PricingStrategy {

    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;

    @Override
    public InterestType interestType() {
        return InterestType.FLAT;
    }

    @Override
    public PricingResult calculate(PricingRequest request) {
        BigDecimal principal = PricingMath.money(request.principalAmount());
        BigDecimal totalInterest = PricingMath.money(principal
                .multiply(request.annualRate(), CALCULATION_CONTEXT)
                .multiply(BigDecimal.valueOf(request.tenorMonths()), CALCULATION_CONTEXT)
                .divide(BigDecimal.valueOf(12), CALCULATION_CONTEXT));
        BigDecimal principalPerInstallment = principal
                .divide(BigDecimal.valueOf(request.tenorMonths()), PricingMath.MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal interestPerInstallment = totalInterest
                .divide(BigDecimal.valueOf(request.tenorMonths()), PricingMath.MONEY_SCALE, RoundingMode.HALF_UP);

        List<Installment> installments = new ArrayList<>(request.tenorMonths());
        BigDecimal remainingPrincipal = principal;
        BigDecimal remainingInterest = totalInterest;
        for (int installmentNo = 1; installmentNo <= request.tenorMonths(); installmentNo++) {
            boolean last = installmentNo == request.tenorMonths();
            BigDecimal principalAmount = last ? remainingPrincipal : PricingMath.money(principalPerInstallment);
            BigDecimal interestAmount = last ? remainingInterest : PricingMath.money(interestPerInstallment);
            BigDecimal totalAmount = PricingMath.money(principalAmount.add(interestAmount));
            installments.add(new Installment(installmentNo, PricingMath.money(principalAmount),
                    PricingMath.money(interestAmount), totalAmount));
            remainingPrincipal = PricingMath.money(remainingPrincipal.subtract(principalAmount));
            remainingInterest = PricingMath.money(remainingInterest.subtract(interestAmount));
        }

        BigDecimal totalRepayment = installments.stream()
                .map(Installment::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PricingResult(totalInterest, PricingMath.money(totalRepayment),
                installments.getFirst().totalAmount(), installments);
    }
}

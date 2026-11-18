package com.fincore.loan.domain.service.pricing;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import com.fincore.loan.domain.enums.InterestType;

public class AnnuityPricingStrategy implements PricingStrategy {

    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;

    @Override
    public InterestType interestType() {
        return InterestType.ANNUITY;
    }

    @Override
    public PricingResult calculate(PricingRequest request) {
        BigDecimal principal = PricingMath.money(request.principalAmount());
        BigDecimal monthlyRate = request.annualRate()
                .divide(BigDecimal.valueOf(12), PricingMath.RATE_SCALE, RoundingMode.HALF_UP);
        BigDecimal monthlyInstallment = calculatePayment(principal, monthlyRate, request.tenorMonths());

        List<Installment> installments = new ArrayList<>(request.tenorMonths());
        BigDecimal remainingPrincipal = principal;
        BigDecimal totalInterest = BigDecimal.ZERO.setScale(PricingMath.MONEY_SCALE);
        for (int installmentNo = 1; installmentNo <= request.tenorMonths(); installmentNo++) {
            boolean last = installmentNo == request.tenorMonths();
            BigDecimal interestAmount = last
                    ? PricingMath.money(remainingPrincipal.multiply(monthlyRate, CALCULATION_CONTEXT))
                    : PricingMath.money(remainingPrincipal.multiply(monthlyRate, CALCULATION_CONTEXT));
            BigDecimal principalAmount = last
                    ? remainingPrincipal
                    : PricingMath.money(monthlyInstallment.subtract(interestAmount));
            BigDecimal totalAmount = PricingMath.money(principalAmount.add(interestAmount));
            installments.add(new Installment(installmentNo, PricingMath.money(principalAmount),
                    PricingMath.money(interestAmount), totalAmount));
            totalInterest = PricingMath.money(totalInterest.add(interestAmount));
            remainingPrincipal = last ? BigDecimal.ZERO.setScale(PricingMath.MONEY_SCALE)
                    : PricingMath.money(remainingPrincipal.subtract(principalAmount));
        }

        BigDecimal totalRepayment = installments.stream()
                .map(Installment::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PricingResult(totalInterest, PricingMath.money(totalRepayment), monthlyInstallment, installments);
    }

    private static BigDecimal calculatePayment(BigDecimal principal, BigDecimal monthlyRate, int tenorMonths) {
        if (monthlyRate.signum() == 0) {
            return principal.divide(BigDecimal.valueOf(tenorMonths), PricingMath.MONEY_SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal factor = BigDecimal.ONE.add(monthlyRate).pow(tenorMonths, CALCULATION_CONTEXT);
        BigDecimal payment = principal.multiply(monthlyRate, CALCULATION_CONTEXT)
                .multiply(factor, CALCULATION_CONTEXT)
                .divide(factor.subtract(BigDecimal.ONE), CALCULATION_CONTEXT);
        return PricingMath.money(payment);
    }
}

package com.fincore.loan.application.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fincore.loan.domain.enums.InterestType;
import com.fincore.loan.domain.model.LoanProduct;
import com.fincore.loan.domain.model.LoanQuote;
import com.fincore.loan.domain.service.pricing.PricingRequest;
import com.fincore.loan.domain.service.pricing.PricingResult;
import com.fincore.loan.domain.service.pricing.PricingStrategy;
import com.fincore.loan.domain.port.LoanProductRepository;
import com.fincore.platform.web.ResourceNotFoundException;

import org.springframework.stereotype.Service;

@Service
public class LoanQuoteService {

    private final LoanProductRepository repository;
    private final Map<InterestType, PricingStrategy> strategies;

    public LoanQuoteService(LoanProductRepository repository, List<PricingStrategy> strategies) {
        this.repository = repository;
        this.strategies = strategies.stream()
                .collect(Collectors.toUnmodifiableMap(PricingStrategy::interestType, Function.identity()));
    }

    public LoanQuote quote(String productCode, BigDecimal principalAmount, int tenorMonths) {
        LoanProduct product = repository.findActiveByCode(productCode)
                .orElseThrow(() -> new ResourceNotFoundException("Active loan product was not found"));
        if (principalAmount.compareTo(product.minAmount()) < 0
                || principalAmount.compareTo(product.maxAmount()) > 0) {
            throw new IllegalArgumentException("principalAmount must be within the product amount range");
        }

        PricingStrategy strategy = strategies.get(product.interestType());
        if (strategy == null) {
            throw new IllegalArgumentException("No pricing strategy configured for " + product.interestType());
        }
        PricingResult result = strategy.calculate(new PricingRequest(
                principalAmount, product.annualRate(), tenorMonths));
        return new LoanQuote(
                product.code(),
                product.interestType(),
                product.annualRate(),
                principalAmount,
                tenorMonths,
                result.totalInterest(),
                result.totalRepayment(),
                result.monthlyInstallment(),
                result.installments());
    }
}

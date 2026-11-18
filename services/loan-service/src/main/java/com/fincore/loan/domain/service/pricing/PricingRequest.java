package com.fincore.loan.domain.service.pricing;

import java.math.BigDecimal;
import java.util.Objects;

public record PricingRequest(BigDecimal principalAmount, BigDecimal annualRate, int tenorMonths) {

    public PricingRequest {
        Objects.requireNonNull(principalAmount, "principalAmount must not be null");
        Objects.requireNonNull(annualRate, "annualRate must not be null");
        if (principalAmount.signum() <= 0) {
            throw new IllegalArgumentException("principalAmount must be greater than zero");
        }
        if (annualRate.signum() < 0) {
            throw new IllegalArgumentException("annualRate must not be negative");
        }
        if (tenorMonths < 1) {
            throw new IllegalArgumentException("tenorMonths must be greater than zero");
        }
    }
}

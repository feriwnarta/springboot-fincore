package com.fincore.loan.domain.service.pricing;

import com.fincore.loan.domain.enums.InterestType;

public interface PricingStrategy {

    InterestType interestType();

    PricingResult calculate(PricingRequest request);
}

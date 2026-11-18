package com.fincore.loan.domain.service.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class PricingMath {

    static final int MONEY_SCALE = 4;
    static final int RATE_SCALE = 12;

    private PricingMath() {
    }

    static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}

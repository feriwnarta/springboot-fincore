package com.fincore.loan.config;

import com.fincore.loan.domain.service.pricing.AnnuityPricingStrategy;
import com.fincore.loan.domain.service.pricing.FlatRatePricingStrategy;
import com.fincore.loan.domain.service.pricing.PricingStrategy;
import com.fincore.loan.domain.service.RepaymentScheduleGenerator;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PricingConfiguration {

    @Bean
    PricingStrategy annuityPricingStrategy() {
        return new AnnuityPricingStrategy();
    }

    @Bean
    PricingStrategy flatRatePricingStrategy() {
        return new FlatRatePricingStrategy();
    }

    @Bean
    RepaymentScheduleGenerator repaymentScheduleGenerator(java.util.List<PricingStrategy> strategies) {
        return new RepaymentScheduleGenerator(strategies);
    }
}

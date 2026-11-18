package com.fincore.loan.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fincore.loan.domain.enums.InterestType;
import com.fincore.loan.domain.model.LoanProduct;
import com.fincore.loan.domain.model.LoanQuote;
import com.fincore.loan.domain.service.pricing.FlatRatePricingStrategy;
import com.fincore.loan.domain.port.LoanProductRepository;

import org.junit.jupiter.api.Test;

class LoanQuoteServiceTest {

    @Test
    void loadsTheActiveProductAndBuildsAQuote() {
        LoanProductRepository repository = mock(LoanProductRepository.class);
        LoanProduct product = new LoanProduct(
                UUID.randomUUID(),
                "PERSONAL_FLAT",
                InterestType.FLAT,
                new BigDecimal("0.120000"),
                new BigDecimal("1000000.0000"),
                new BigDecimal("50000000.0000"),
                true,
                1,
                Instant.parse("2026-11-17T03:00:00Z"));
        when(repository.findActiveByCode("PERSONAL_FLAT")).thenReturn(Optional.of(product));

        LoanQuote quote = new LoanQuoteService(repository, List.of(new FlatRatePricingStrategy()))
                .quote("PERSONAL_FLAT", new BigDecimal("1000000.0000"), 12);

        assertThat(quote.productCode()).isEqualTo("PERSONAL_FLAT");
        assertThat(quote.interestType()).isEqualTo(InterestType.FLAT);
        assertThat(quote.totalRepayment()).isEqualByComparingTo("1120000.0000");
        assertThat(quote.installments()).hasSize(12);
    }
}

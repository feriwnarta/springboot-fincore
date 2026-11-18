package com.fincore.loan.api.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LoanQuoteRequest(
        @NotBlank String productCode,
        @NotNull @DecimalMin(value = "0.0001") @Digits(integer = 15, fraction = 4)
        BigDecimal principalAmount,
        @NotNull @Min(1) @Max(480) Integer tenorMonths) {
}

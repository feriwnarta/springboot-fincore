package com.fincore.loan.api.dto.request;

import java.math.BigDecimal;

import com.fincore.loan.domain.enums.InterestType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateLoanProductRequest(
        @NotBlank @Size(max = 50) String code,
        @NotNull InterestType interestType,
        @NotNull @DecimalMin("0.000000") @Digits(integer = 3, fraction = 6) BigDecimal annualRate,
        @NotNull @DecimalMin("0.0001") @Digits(integer = 15, fraction = 4) BigDecimal minAmount,
        @NotNull @DecimalMin("0.0001") @Digits(integer = 15, fraction = 4) BigDecimal maxAmount,
        Boolean active) {

    public boolean isActive() {
        return active == null || active;
    }
}

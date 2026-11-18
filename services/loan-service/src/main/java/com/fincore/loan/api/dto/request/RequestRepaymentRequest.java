package com.fincore.loan.api.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record RequestRepaymentRequest(
        @NotNull @DecimalMin(value = "0.0001") @Digits(integer = 15, fraction = 4)
        BigDecimal amount) {
}

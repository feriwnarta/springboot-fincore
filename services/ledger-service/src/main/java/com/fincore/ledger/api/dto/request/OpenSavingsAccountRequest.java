package com.fincore.ledger.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record OpenSavingsAccountRequest(@NotBlank String customerId) {
}

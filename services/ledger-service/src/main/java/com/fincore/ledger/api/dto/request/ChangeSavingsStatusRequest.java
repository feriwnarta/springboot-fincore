package com.fincore.ledger.api.dto.request;

import com.fincore.ledger.domain.enums.SavingsAccountStatus;

import jakarta.validation.constraints.NotNull;

public record ChangeSavingsStatusRequest(@NotNull SavingsAccountStatus status) {
}

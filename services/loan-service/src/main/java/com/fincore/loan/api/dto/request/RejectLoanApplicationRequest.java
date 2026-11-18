package com.fincore.loan.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectLoanApplicationRequest(
        @NotBlank String reasonCode,
        @NotBlank @Size(max = 500) String reason) {
}

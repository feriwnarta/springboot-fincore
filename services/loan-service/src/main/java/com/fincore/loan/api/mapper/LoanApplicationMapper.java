package com.fincore.loan.api.mapper;

import java.math.RoundingMode;

import com.fincore.loan.api.dto.response.LoanApplicationResponse;
import com.fincore.loan.domain.model.LoanApplication;

public final class LoanApplicationMapper {

    private LoanApplicationMapper() {
    }

    public static LoanApplicationResponse toResponse(LoanApplication application) {
        return new LoanApplicationResponse(
                application.id().toString(),
                application.customerId(),
                application.productCode(),
                application.principalAmount().setScale(4, RoundingMode.UNNECESSARY).toPlainString(),
                application.tenorMonths(),
                application.status().name(),
                application.version(),
                application.businessReference(),
                application.createdAt());
    }
}

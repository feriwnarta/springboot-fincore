package com.fincore.loan.api.mapper;

import java.math.RoundingMode;

import com.fincore.loan.api.dto.response.LoanFundingResponse;
import com.fincore.loan.domain.model.LoanFunding;

public final class LoanFundingMapper {

    private LoanFundingMapper() {
    }

    public static LoanFundingResponse toResponse(LoanFunding funding) {
        return new LoanFundingResponse(
                funding.id().toString(),
                funding.applicationId().toString(),
                funding.customerId(),
                funding.productCode(),
                funding.principalAmount().setScale(4, RoundingMode.UNNECESSARY).toPlainString(),
                funding.status().name(),
                funding.ledgerPostingStatus().name(),
                funding.version(),
                funding.fundedAt(),
                funding.createdAt(),
                funding.updatedAt());
    }
}

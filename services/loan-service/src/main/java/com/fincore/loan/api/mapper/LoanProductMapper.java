package com.fincore.loan.api.mapper;

import java.math.RoundingMode;

import com.fincore.loan.api.dto.response.LoanProductResponse;
import com.fincore.loan.domain.model.LoanProduct;

public final class LoanProductMapper {

    private LoanProductMapper() {
    }

    public static LoanProductResponse toResponse(LoanProduct product) {
        return new LoanProductResponse(
                product.id().toString(),
                product.code(),
                product.interestType().name(),
                product.annualRate().setScale(6, RoundingMode.UNNECESSARY).toPlainString(),
                product.minAmount().setScale(4, RoundingMode.UNNECESSARY).toPlainString(),
                product.maxAmount().setScale(4, RoundingMode.UNNECESSARY).toPlainString(),
                product.active(),
                product.version(),
                product.updatedAt());
    }
}

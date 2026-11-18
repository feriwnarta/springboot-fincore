package com.fincore.loan.api.mapper;

import java.math.RoundingMode;

import com.fincore.loan.api.dto.response.RepaymentResponse;
import com.fincore.loan.domain.model.Repayment;

public final class RepaymentMapper {

    private RepaymentMapper() {
    }

    public static RepaymentResponse toResponse(Repayment repayment) {
        return new RepaymentResponse(
                repayment.id().toString(),
                repayment.loanId().toString(),
                repayment.businessReference(),
                repayment.amount().setScale(4, RoundingMode.UNNECESSARY).toPlainString(),
                repayment.status().name(),
                repayment.simulatedReceiptReference(),
                repayment.ledgerReference(),
                repayment.createdAt(),
                repayment.postedAt());
    }
}

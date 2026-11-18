package com.fincore.loan.api.mapper;

import java.math.RoundingMode;

import com.fincore.loan.api.dto.response.InstallmentResponse;
import com.fincore.loan.api.dto.response.LoanQuoteResponse;
import com.fincore.loan.domain.model.LoanQuote;

public final class LoanQuoteMapper {

    private LoanQuoteMapper() {
    }

    public static LoanQuoteResponse toResponse(LoanQuote quote) {
        return new LoanQuoteResponse(
                quote.productCode(),
                quote.interestType().name(),
                quote.annualRate().setScale(6, RoundingMode.UNNECESSARY).toPlainString(),
                quote.principalAmount().setScale(4, RoundingMode.UNNECESSARY).toPlainString(),
                quote.tenorMonths(),
                quote.totalInterest().setScale(4, RoundingMode.UNNECESSARY).toPlainString(),
                quote.totalRepayment().setScale(4, RoundingMode.UNNECESSARY).toPlainString(),
                quote.monthlyInstallment().setScale(4, RoundingMode.UNNECESSARY).toPlainString(),
                quote.installments().stream()
                        .map(installment -> new InstallmentResponse(
                                installment.installmentNo(),
                                installment.principalAmount().setScale(4, RoundingMode.UNNECESSARY).toPlainString(),
                                installment.interestAmount().setScale(4, RoundingMode.UNNECESSARY).toPlainString(),
                                installment.totalAmount().setScale(4, RoundingMode.UNNECESSARY).toPlainString()))
                        .toList());
    }
}

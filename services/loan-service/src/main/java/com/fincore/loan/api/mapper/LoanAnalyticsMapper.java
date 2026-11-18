package com.fincore.loan.api.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

import com.fincore.loan.api.dto.response.RunningFundedTotalReportResponse;
import com.fincore.loan.api.dto.response.TopCustomerReportResponse;
import com.fincore.loan.api.dto.response.TopFundedLoanReportResponse;
import com.fincore.loan.application.query.RunningFundedTotalReportRow;
import com.fincore.loan.application.query.TopCustomerReportRow;
import com.fincore.loan.application.query.TopFundedLoanReportRow;

public final class LoanAnalyticsMapper {

    private LoanAnalyticsMapper() {
    }

    public static List<TopCustomerReportResponse> toTopCustomerResponses(List<TopCustomerReportRow> rows) {
        return rows.stream()
                .map(row -> new TopCustomerReportResponse(
                        row.customerId(), money(row.fundedPrincipal())))
                .toList();
    }

    public static List<TopFundedLoanReportResponse> toTopFundedLoanResponses(List<TopFundedLoanReportRow> rows) {
        return rows.stream()
                .map(row -> new TopFundedLoanReportResponse(
                        row.loanId(), row.customerId(), money(row.principalAmount()),
                        timestamp(row.fundedAt()), row.rank()))
                .toList();
    }

    public static List<RunningFundedTotalReportResponse> toRunningFundedTotalResponses(
            List<RunningFundedTotalReportRow> rows) {
        return rows.stream()
                .map(row -> new RunningFundedTotalReportResponse(
                        row.loanId(), row.productCode(), timestamp(row.fundedAt()),
                        money(row.principalAmount()), money(row.runningPrincipal())))
                .toList();
    }

    private static String money(BigDecimal value) {
        return value.setScale(4, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static String timestamp(Instant value) {
        return value.toString();
    }
}

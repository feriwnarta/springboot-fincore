package com.fincore.loan.application.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fincore.loan.application.query.FundedLoanReportRow;
import com.fincore.loan.application.query.RepaymentAggregateReportRow;
import com.fincore.loan.application.query.RepaymentReportRow;
import com.fincore.loan.application.query.RunningFundedTotalReportRow;
import com.fincore.loan.application.query.TopCustomerReportRow;
import com.fincore.loan.application.query.TopFundedLoanReportRow;
import com.fincore.loan.application.query.TopRepaidLoanReportRow;
import com.fincore.loan.application.port.LoanAnalyticsRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoanAnalyticsService {

    public static final int MAX_BOUNDED_DATASET = 1_000;
    private static final int REPORT_LIMIT = 5;

    private final LoanAnalyticsRepository repository;

    public List<TopCustomerReportRow> topCustomersFromBoundedDataset(
            Instant from, Instant to, int requestedLimit) {
        validateRange(from, to);
        int limit = validateBoundedLimit(requestedLimit);
        Map<String, BigDecimal> totalsByCustomer = repository.findFundedLoans(from, to, limit).stream()
                .filter(row -> row.principalAmount().signum() > 0)
                .map(Function.identity())
                .collect(Collectors.groupingBy(
                        FundedLoanReportRow::customerId,
                        Collectors.reducing(BigDecimal.ZERO,
                                FundedLoanReportRow::principalAmount,
                                BigDecimal::add)));

        return totalsByCustomer.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .limit(REPORT_LIMIT)
                .map(entry -> new TopCustomerReportRow(entry.getKey(), entry.getValue()))
                .toList();
    }

    public List<TopRepaidLoanReportRow> topRepaidLoansFromBoundedDataset(
            Instant from, Instant to, int requestedLimit) {
        validateRange(from, to);
        int limit = validateBoundedLimit(requestedLimit);
        Map<UUID, BigDecimal> totalsByLoan = repository.findRepayments(from, to, limit).stream()
                .filter(row -> "POSTED".equals(row.status()))
                .map(Function.identity())
                .collect(Collectors.groupingBy(
                        RepaymentReportRow::loanId,
                        Collectors.reducing(BigDecimal.ZERO,
                                RepaymentReportRow::amount,
                                BigDecimal::add)));

        return totalsByLoan.entrySet().stream()
                .sorted(Map.Entry.<UUID, BigDecimal>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(entry -> entry.getKey().toString()))
                .limit(REPORT_LIMIT)
                .map(entry -> new TopRepaidLoanReportRow(entry.getKey(), entry.getValue()))
                .toList();
    }

    public List<TopFundedLoanReportRow> topThreeFundedLoansPerCustomer(Instant from, Instant to) {
        validateRange(from, to);
        return repository.findTopThreeFundedLoansPerCustomer(from, to);
    }

    public List<RunningFundedTotalReportRow> runningFundedTotals(Instant from, Instant to) {
        validateRange(from, to);
        return repository.findRunningFundedTotals(from, to);
    }

    public List<RepaymentAggregateReportRow> postedRepaymentAggregates(Instant from, Instant to) {
        validateRange(from, to);
        return repository.findPostedRepaymentAggregates(from, to);
    }

    private static void validateRange(Instant from, Instant to) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
    }

    private static int validateBoundedLimit(int requestedLimit) {
        if (requestedLimit < 1 || requestedLimit > MAX_BOUNDED_DATASET) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_BOUNDED_DATASET);
        }
        return requestedLimit;
    }
}

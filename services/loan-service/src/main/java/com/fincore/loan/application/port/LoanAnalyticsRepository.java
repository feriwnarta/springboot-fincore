package com.fincore.loan.application.port;

import java.time.Instant;
import java.util.List;

import com.fincore.loan.application.query.FundedLoanReportRow;
import com.fincore.loan.application.query.RepaymentAggregateReportRow;
import com.fincore.loan.application.query.RepaymentReportRow;
import com.fincore.loan.application.query.RunningFundedTotalReportRow;
import com.fincore.loan.application.query.TopFundedLoanReportRow;

public interface LoanAnalyticsRepository {

    List<FundedLoanReportRow> findFundedLoans(Instant from, Instant to, int limit);

    List<RepaymentReportRow> findRepayments(Instant from, Instant to, int limit);

    List<TopFundedLoanReportRow> findTopThreeFundedLoansPerCustomer(Instant from, Instant to);

    List<RunningFundedTotalReportRow> findRunningFundedTotals(Instant from, Instant to);

    List<RepaymentAggregateReportRow> findPostedRepaymentAggregates(Instant from, Instant to);
}

package com.fincore.loan.api.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import com.fincore.loan.api.dto.response.RunningFundedTotalReportResponse;
import com.fincore.loan.api.dto.response.TopCustomerReportResponse;
import com.fincore.loan.api.dto.response.TopFundedLoanReportResponse;
import com.fincore.loan.api.mapper.LoanAnalyticsMapper;
import com.fincore.loan.application.service.LoanAnalyticsService;
import com.fincore.platform.api.ApiMetaFactory;
import com.fincore.platform.api.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports/loans")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class LoanReportController {

    private final LoanAnalyticsService analyticsService;

    @GetMapping("/top-customers")
    public ApiResponse<List<TopCustomerReportResponse>> topCustomers(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1000") int limit,
            HttpServletRequest request) {
        var data = LoanAnalyticsMapper.toTopCustomerResponses(
                analyticsService.topCustomersFromBoundedDataset(fromInclusive(from), toExclusive(to), limit));
        return ApiResponse.success(data, ApiMetaFactory.from(request));
    }

    @GetMapping("/top-three")
    public ApiResponse<List<TopFundedLoanReportResponse>> topThree(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpServletRequest request) {
        var data = LoanAnalyticsMapper.toTopFundedLoanResponses(
                analyticsService.topThreeFundedLoansPerCustomer(fromInclusive(from), toExclusive(to)));
        return ApiResponse.success(data, ApiMetaFactory.from(request));
    }

    @GetMapping("/running-total")
    public ApiResponse<List<RunningFundedTotalReportResponse>> runningTotal(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpServletRequest request) {
        var data = LoanAnalyticsMapper.toRunningFundedTotalResponses(
                analyticsService.runningFundedTotals(fromInclusive(from), toExclusive(to)));
        return ApiResponse.success(data, ApiMetaFactory.from(request));
    }

    private static Instant fromInclusive(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static Instant toExclusive(LocalDate date) {
        return date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}

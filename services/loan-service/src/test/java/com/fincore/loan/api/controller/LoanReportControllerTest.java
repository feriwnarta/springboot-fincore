package com.fincore.loan.api.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import com.fincore.loan.application.query.TopCustomerReportRow;
import com.fincore.loan.application.service.LoanAnalyticsService;
import com.fincore.platform.web.ApiExceptionHandler;
import com.fincore.platform.web.RequestContextFilter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LoanReportControllerTest {

    private LoanAnalyticsService analyticsService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        analyticsService = mock(LoanAnalyticsService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new LoanReportController(analyticsService))
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new RequestContextFilter())
                .build();
    }

    @Test
    void returnsTopCustomersForDateOnlyRangeUsingTheCommonResponseEnvelope() throws Exception {
        Instant from = LocalDate.of(2026, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = LocalDate.of(2027, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
        when(analyticsService.topCustomersFromBoundedDataset(from, to, 1000))
                .thenReturn(List.of(new TopCustomerReportRow("customer-001", new BigDecimal("1500000.0000"))));

        mockMvc.perform(get("/api/v1/reports/loans/top-customers")
                        .param("from", "2026-01-01")
                        .param("to", "2026-12-31")
                        .header("X-Correlation-Id", "11111111-1111-4111-8111-000000000061"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "11111111-1111-4111-8111-000000000061"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].customerId").value("customer-001"))
                .andExpect(jsonPath("$.data[0].fundedPrincipal").value("1500000.0000"))
                .andExpect(jsonPath("$.meta.correlationId").value("11111111-1111-4111-8111-000000000061"));
    }
}

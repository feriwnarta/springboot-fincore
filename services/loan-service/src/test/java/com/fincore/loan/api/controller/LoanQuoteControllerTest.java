package com.fincore.loan.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fincore.loan.application.service.LoanQuoteService;
import com.fincore.loan.domain.enums.InterestType;
import com.fincore.loan.domain.model.LoanQuote;
import com.fincore.loan.domain.service.pricing.Installment;
import com.fincore.platform.web.ApiExceptionHandler;
import com.fincore.platform.web.RequestContextFilter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LoanQuoteControllerTest {

    private LoanQuoteService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(LoanQuoteService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new LoanQuoteController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new RequestContextFilter())
                .build();
    }

    @Test
    void returnsAQuoteUsingTheCommonResponseEnvelope() throws Exception {
        when(service.quote("PERSONAL_FLAT", new BigDecimal("1000000.0000"), 12))
                .thenReturn(new LoanQuote(
                        "PERSONAL_FLAT",
                        InterestType.FLAT,
                        new BigDecimal("0.120000"),
                        new BigDecimal("1000000.0000"),
                        12,
                        new BigDecimal("120000.0000"),
                        new BigDecimal("1120000.0000"),
                        new BigDecimal("93333.3333"),
                        List.of(new Installment(1, new BigDecimal("83333.3333"),
                                new BigDecimal("10000.0000"), new BigDecimal("93333.3333")))));

        mockMvc.perform(post("/api/v1/loan-quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-Id", "11111111-1111-4111-8111-000000000051")
                        .header("X-Request-Id", "quote-req-001")
                        .content("""
                                {
                                  "productCode": "PERSONAL_FLAT",
                                  "principalAmount": "1000000.0000",
                                  "tenorMonths": 12
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "11111111-1111-4111-8111-000000000051"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.productCode").value("PERSONAL_FLAT"))
                .andExpect(jsonPath("$.data.interestType").value("FLAT"))
                .andExpect(jsonPath("$.data.totalRepayment").value("1120000.0000"))
                .andExpect(jsonPath("$.data.installments[0].totalAmount").value("93333.3333"))
                .andExpect(jsonPath("$.meta.correlationId").value("11111111-1111-4111-8111-000000000051"));
    }

    @Test
    void rejectsAnInvalidPrincipalWithValidationEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/loan-quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "PERSONAL_FLAT",
                                  "principalAmount": "0",
                                  "tenorMonths": 12
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}

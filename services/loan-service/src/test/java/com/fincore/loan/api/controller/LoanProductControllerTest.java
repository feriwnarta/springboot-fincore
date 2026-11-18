package com.fincore.loan.api.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fincore.loan.application.service.LoanProductService;
import com.fincore.loan.application.command.CreateLoanProductCommand;
import com.fincore.loan.domain.enums.InterestType;
import com.fincore.loan.domain.model.LoanProduct;
import com.fincore.platform.web.ApiExceptionHandler;
import com.fincore.platform.web.RequestContextFilter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LoanProductControllerTest {

    private LoanProductService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(LoanProductService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new LoanProductController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new RequestContextFilter())
                .build();
    }

    @Test
    void createsAProductUsingTheCommonResponseEnvelope() throws Exception {
        LoanProduct product = product("KTA_ANNUITY");
        when(service.create(any(CreateLoanProductCommand.class)))
                .thenReturn(product);

        mockMvc.perform(post("/api/v1/loan-products")
                        .header("Idempotency-Key", "product-create-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "KTA_ANNUITY",
                                  "interestType": "ANNUITY",
                                  "annualRate": 0.12,
                                  "minAmount": 1000000,
                                  "maxAmount": 50000000,
                                  "active": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("KTA_ANNUITY"))
                .andExpect(jsonPath("$.data.interestType").value("ANNUITY"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void rejectsProductCreationWithoutIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/loan-products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"KTA_ANNUITY\",\"interestType\":\"ANNUITY\","
                                + "\"annualRate\":0.12,\"minAmount\":1000000,\"maxAmount\":50000000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("MISSING_HEADER"));
    }

    private static LoanProduct product(String code) {
        return new LoanProduct(
                UUID.randomUUID(),
                code,
                InterestType.ANNUITY,
                new BigDecimal("0.120000"),
                new BigDecimal("1000000.0000"),
                new BigDecimal("50000000.0000"),
                true,
                1,
                Instant.parse("2026-11-17T03:00:00Z"));
    }
}

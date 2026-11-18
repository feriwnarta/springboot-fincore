package com.fincore.loan.api.controller;

import static com.fincore.loan.testsupport.JwtTestIdentity.authenticate;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fincore.loan.domain.enums.LoanApplicationStatus;
import com.fincore.loan.domain.model.LoanApplication;
import com.fincore.loan.application.service.LoanApplicationService;
import com.fincore.platform.security.SpringCurrentIdentity;
import com.fincore.platform.web.ApiExceptionHandler;
import com.fincore.platform.web.RequestContextFilter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LoanApplicationControllerTest {

    private LoanApplicationService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(LoanApplicationService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new LoanApplicationController(service, new SpringCurrentIdentity()))
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new RequestContextFilter())
                .build();
    }

    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void submitsApplicationWithTheStandardResponseContract() throws Exception {
        authenticate("actor-001", null, "LOAN_OPERATOR");
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(service.submit(org.mockito.ArgumentMatchers.any())).thenReturn(new LoanApplication(
                id, "customer-001", "PERSONAL_FLAT", new BigDecimal("1000000.0000"), 12,
                LoanApplicationStatus.SUBMITTED, 1, Instant.parse("2026-11-17T03:00:00Z"),
                null, null, "actor-001", "APP-001"));

        mockMvc.perform(post("/api/v1/loan-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "idem-001")
                        .header("X-Correlation-Id", "11111111-1111-4111-8111-000000000021")
                        .header("X-Request-Id", "req-001")
                        .content("""
                                {
                                  "customerId": "customer-001",
                                  "productCode": "PERSONAL_FLAT",
                                  "principalAmount": "1000000.0000",
                                  "tenorMonths": 12
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Correlation-Id", "11111111-1111-4111-8111-000000000021"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.applicationId").value(id.toString()))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.principalAmount").value("1000000.0000"))
                .andExpect(jsonPath("$.meta.correlationId").value("11111111-1111-4111-8111-000000000021"));
    }

    @Test
    void rejectsAnInvalidApplicationRequest() throws Exception {
        mockMvc.perform(post("/api/v1/loan-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "idem-001")
                        .content("""
                                {
                                  "customerId": "",
                                  "productCode": "PERSONAL_FLAT",
                                  "principalAmount": "0",
                                  "tenorMonths": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void approvesApplicationWithTheStandardResponseContract() throws Exception {
        authenticate("actor-001", null, "LOAN_APPROVER");
        UUID id = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(service.approve(org.mockito.ArgumentMatchers.any())).thenReturn(new LoanApplication(
                id, "customer-001", "PERSONAL_FLAT", new BigDecimal("1000000.0000"), 12,
                LoanApplicationStatus.APPROVED, 2, Instant.parse("2026-11-17T03:00:00Z"),
                Instant.parse("2026-11-17T03:05:00Z"), null, "actor-001", "APP-002"));

        mockMvc.perform(post("/api/v1/loan-applications/{id}/approve", id)
                        .header("Idempotency-Key", "approve-idem-001")
                        .header("X-Correlation-Id", "11111111-1111-4111-8111-000000000022")
                        .header("X-Request-Id", "approve-req-001"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "11111111-1111-4111-8111-000000000022"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.applicationId").value(id.toString()))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.version").value(2));
    }

    @Test
    void rejectsApplicationWithAValidatedReasonAndTheStandardResponseContract() throws Exception {
        authenticate("actor-001", null, "LOAN_APPROVER");
        UUID id = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(service.reject(org.mockito.ArgumentMatchers.any())).thenReturn(new LoanApplication(
                id, "customer-001", "PERSONAL_FLAT", new BigDecimal("1000000.0000"), 12,
                LoanApplicationStatus.REJECTED, 2, Instant.parse("2026-11-17T03:00:00Z"),
                null, Instant.parse("2026-11-17T03:05:00Z"), "actor-001", "APP-003"));

        mockMvc.perform(post("/api/v1/loan-applications/{id}/reject", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "reject-idem-001")
                        .content("""
                                {
                                  "reasonCode": "CREDIT_POLICY",
                                  "reason": "Debt service ratio is too high"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.applicationId").value(id.toString()))
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    void rejectsAnInvalidDecisionReason() throws Exception {
        mockMvc.perform(post("/api/v1/loan-applications/{id}/reject", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "reject-idem-002")
                        .content("""
                                {
                                  "reasonCode": "",
                                  "reason": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void requiresIdempotencyKeyForApproval() throws Exception {
        mockMvc.perform(post("/api/v1/loan-applications/{id}/approve", UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MISSING_HEADER"));
    }

}

package com.fincore.loan.api.controller;

import static com.fincore.loan.testsupport.JwtTestIdentity.authenticate;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fincore.loan.application.service.LoanFundingService;
import com.fincore.loan.domain.enums.LedgerPostingStatus;
import com.fincore.loan.domain.enums.LoanFundingStatus;
import com.fincore.loan.domain.model.LoanFunding;
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

class LoanFundingControllerTest {

    private LoanFundingService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(LoanFundingService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new LoanFundingController(service, new SpringCurrentIdentity()))
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new RequestContextFilter())
                .build();
    }

    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsFundingAsTheCommonDtoEnvelope() throws Exception {
        authenticate("actor-001", null, "LOAN_OPERATOR");
        UUID loanId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        when(service.requestFunding(org.mockito.ArgumentMatchers.any())).thenReturn(new LoanFunding(
                loanId, loanId, "customer-001", "PERSONAL_FLAT", new BigDecimal("1000000.0000"),
                LoanFundingStatus.FUNDED, LedgerPostingStatus.PENDING, 2,
                Instant.parse("2026-11-17T03:00:00Z"), Instant.parse("2026-11-17T02:59:00Z"),
                Instant.parse("2026-11-17T03:00:00Z")));

        mockMvc.perform(post("/api/v1/loans/{id}/fund", loanId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "fund-001")
                        .header("X-Actor-Id", "spoofed-id").header("X-Actor-Role", "LOAN_APPROVER")
                        .header("X-Correlation-Id", "11111111-1111-4111-8111-000000000031")
                        .header("X-Request-Id", "req-001")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "11111111-1111-4111-8111-000000000031"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.loanId").value(loanId.toString()))
                .andExpect(jsonPath("$.data.fundingStatus").value("FUNDED"))
                .andExpect(jsonPath("$.data.ledgerPostingStatus").value("PENDING"))
                .andExpect(jsonPath("$.meta.correlationId").value("11111111-1111-4111-8111-000000000031"));
        verify(service).requestFunding(argThat(command ->
                "actor-001".equals(command.actorId()) && "LOAN_OPERATOR".equals(command.actorRole())));
    }
}

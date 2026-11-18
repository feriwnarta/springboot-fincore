package com.fincore.loan.api.controller;

import java.util.UUID;

import com.fincore.loan.api.dto.request.FundLoanRequest;
import com.fincore.loan.api.dto.response.LoanFundingResponse;
import com.fincore.loan.api.mapper.LoanFundingMapper;
import com.fincore.loan.application.command.RequestFundingCommand;
import com.fincore.loan.application.service.LoanFundingService;
import com.fincore.platform.api.ApiMetaFactory;
import com.fincore.platform.api.ApiResponse;
import com.fincore.platform.security.AuditActor;
import com.fincore.platform.security.CurrentIdentity;

import jakarta.servlet.http.HttpServletRequest;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/loans")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class LoanFundingController {

    private final LoanFundingService fundingService;
    private final CurrentIdentity currentIdentity;

    @PostMapping("/{applicationId}/fund")
    public ResponseEntity<ApiResponse<LoanFundingResponse>> fund(
            @PathVariable UUID applicationId,
            @RequestBody(required = false) FundLoanRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest) {
        var actor = AuditActor.from(currentIdentity, "LOAN_OPERATOR", "LOAN_APPROVER");
        var meta = ApiMetaFactory.from(httpRequest);
        var funding = fundingService.requestFunding(new RequestFundingCommand(
                applicationId,
                request != null && request.shouldSimulateFailure(),
                idempotencyKey,
                actor.id(),
                actor.role(),
                meta.correlationId(),
                meta.requestId()));
        return ResponseEntity.ok(ApiResponse.success(LoanFundingMapper.toResponse(funding), meta));
    }
}

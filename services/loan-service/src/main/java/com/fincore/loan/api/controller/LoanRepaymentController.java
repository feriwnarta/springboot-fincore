package com.fincore.loan.api.controller;

import java.util.UUID;

import com.fincore.loan.api.dto.request.RequestRepaymentRequest;
import com.fincore.loan.api.dto.response.RepaymentResponse;
import com.fincore.loan.api.mapper.RepaymentMapper;
import com.fincore.loan.application.command.RequestRepaymentCommand;
import com.fincore.loan.application.service.LoanRepaymentService;
import com.fincore.platform.api.ApiMetaFactory;
import com.fincore.platform.api.ApiResponse;
import com.fincore.platform.security.AuditActor;
import com.fincore.platform.security.CurrentIdentity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

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
public class LoanRepaymentController {

    private final LoanRepaymentService repaymentService;
    private final CurrentIdentity currentIdentity;

    @PostMapping("/{loanId}/repayments")
    public ResponseEntity<ApiResponse<RepaymentResponse>> repay(
            @PathVariable UUID loanId,
            @Valid @RequestBody RequestRepaymentRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest) {
        var actor = AuditActor.from(currentIdentity, "LOAN_OPERATOR", "LOAN_APPROVER");
        var meta = ApiMetaFactory.from(httpRequest);
        var repayment = repaymentService.request(new RequestRepaymentCommand(
                loanId, request.amount(), idempotencyKey, actor.id(), actor.role(),
                meta.correlationId(), meta.requestId()));
        return ResponseEntity.accepted().body(ApiResponse.success(RepaymentMapper.toResponse(repayment), meta));
    }
}

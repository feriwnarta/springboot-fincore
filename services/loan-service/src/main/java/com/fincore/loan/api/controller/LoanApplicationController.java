package com.fincore.loan.api.controller;

import java.util.UUID;

import com.fincore.loan.api.dto.request.RejectLoanApplicationRequest;
import com.fincore.loan.api.dto.request.SubmitLoanApplicationRequest;
import com.fincore.loan.api.dto.response.LoanApplicationResponse;
import com.fincore.loan.api.mapper.LoanApplicationMapper;
import com.fincore.loan.application.command.LoanApplicationDecisionCommand;
import com.fincore.loan.application.command.SubmitLoanApplicationCommand;
import com.fincore.loan.application.service.LoanApplicationService;
import com.fincore.platform.api.ApiMetaFactory;
import com.fincore.platform.api.ApiResponse;
import com.fincore.platform.security.AuditActor;
import com.fincore.platform.security.CurrentIdentity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/loan-applications")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class LoanApplicationController {

    private final LoanApplicationService service;
    private final CurrentIdentity currentIdentity;

    @PostMapping
    public ResponseEntity<ApiResponse<LoanApplicationResponse>> submit(
            @Valid @RequestBody SubmitLoanApplicationRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest) {
        var actor = AuditActor.from(currentIdentity, "LOAN_OPERATOR", "LOAN_APPROVER", "CUSTOMER");
        String customerId = submissionCustomerId(request.customerId(), actor);
        var meta = ApiMetaFactory.from(httpRequest);
        var application = service.submit(new SubmitLoanApplicationCommand(
                customerId, request.productCode(), request.principalAmount(), request.tenorMonths(),
                idempotencyKey, actor.id(), actor.role(), meta.correlationId(), meta.requestId()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(LoanApplicationMapper.toResponse(application), meta));
    }

    @PostMapping("/{applicationId}/approve")
    public ResponseEntity<ApiResponse<LoanApplicationResponse>> approve(
            @PathVariable UUID applicationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest) {
        var actor = AuditActor.from(currentIdentity, "LOAN_APPROVER");
        var meta = ApiMetaFactory.from(httpRequest);
        var application = service.approve(new LoanApplicationDecisionCommand(
                applicationId, idempotencyKey, actor.id(), actor.role(),
                meta.correlationId(), meta.requestId(), null, null));
        return ResponseEntity.ok(ApiResponse.success(LoanApplicationMapper.toResponse(application), meta));
    }

    @PostMapping("/{applicationId}/reject")
    public ResponseEntity<ApiResponse<LoanApplicationResponse>> reject(
            @PathVariable UUID applicationId,
            @Valid @RequestBody RejectLoanApplicationRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest) {
        var actor = AuditActor.from(currentIdentity, "LOAN_APPROVER");
        var meta = ApiMetaFactory.from(httpRequest);
        var application = service.reject(new LoanApplicationDecisionCommand(
                applicationId, idempotencyKey, actor.id(), actor.role(),
                meta.correlationId(), meta.requestId(), request.reasonCode(), request.reason()));
        return ResponseEntity.ok(ApiResponse.success(LoanApplicationMapper.toResponse(application), meta));
    }

    private String submissionCustomerId(String requestedCustomerId, AuditActor actor) {
        if (!"CUSTOMER".equals(actor.role())) {
            return requestedCustomerId;
        }
        return currentIdentity.customerId()
                .filter(requestedCustomerId::equals)
                .orElseThrow(() -> new AccessDeniedException("Customer access denied"));
    }
}

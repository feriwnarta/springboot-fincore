package com.fincore.loan.api.controller;

import com.fincore.loan.api.dto.request.CreateLoanProductRequest;
import com.fincore.loan.api.dto.response.LoanProductResponse;
import com.fincore.loan.api.mapper.LoanProductMapper;
import com.fincore.loan.application.command.CreateLoanProductCommand;
import com.fincore.loan.application.service.LoanProductService;
import com.fincore.platform.api.ApiMetaFactory;
import com.fincore.platform.api.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/loan-products")
@Tag(name = "Loan Products")
@RequiredArgsConstructor
public class LoanProductController {

    private final LoanProductService service;

    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create a loan product")
    public ResponseEntity<ApiResponse<LoanProductResponse>> create(
            @Valid @RequestBody CreateLoanProductRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest) {
        var meta = ApiMetaFactory.from(httpRequest);
        var product = service.create(new CreateLoanProductCommand(
                request.code(), request.interestType(), request.annualRate(), request.minAmount(),
                request.maxAmount(), request.isActive(), idempotencyKey,
                meta.correlationId(), meta.requestId()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(LoanProductMapper.toResponse(product), meta));
    }

}

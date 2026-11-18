package com.fincore.loan.api.controller;

import com.fincore.loan.api.dto.request.LoanQuoteRequest;
import com.fincore.loan.api.dto.response.LoanQuoteResponse;
import com.fincore.loan.api.mapper.LoanQuoteMapper;
import com.fincore.loan.application.service.LoanQuoteService;
import com.fincore.platform.api.ApiMetaFactory;
import com.fincore.platform.api.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/loan-quotes")
@RequiredArgsConstructor
public class LoanQuoteController {

    private final LoanQuoteService service;

    @PostMapping
    public ApiResponse<LoanQuoteResponse> quote(
            @Valid @RequestBody LoanQuoteRequest request,
            HttpServletRequest httpRequest) {
        LoanQuoteResponse response = LoanQuoteMapper.toResponse(
                service.quote(request.productCode(), request.principalAmount(), request.tenorMonths()));
        return ApiResponse.success(response, ApiMetaFactory.from(httpRequest));
    }
}

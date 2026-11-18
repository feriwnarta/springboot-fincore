package com.fincore.search.api.controller;

import com.fincore.platform.api.ApiMetaFactory;
import com.fincore.platform.api.ApiResponse;
import com.fincore.platform.api.PageData;
import com.fincore.search.api.dto.response.LoanApplicationSearchResponse;
import com.fincore.search.api.dto.response.LoanProductSearchResponse;
import com.fincore.search.application.service.LoanSearchQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CatalogSearchController {

    private final LoanSearchQueryService queryService;

    @GetMapping("/loan-products")
    @Operation(summary = "List active loan products from Elasticsearch")
    public ApiResponse<PageData<LoanProductSearchResponse>> listProducts(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor,
            HttpServletRequest request) {
        return ApiResponse.success(queryService.listProducts(limit, cursor), ApiMetaFactory.from(request));
    }

    @GetMapping("/loan-applications")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "List loan applications from Elasticsearch")
    public ApiResponse<PageData<LoanApplicationSearchResponse>> listApplications(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String productCode,
            HttpServletRequest request) {
        return ApiResponse.success(queryService.listApplications(status, customerId, productCode, limit, cursor),
                ApiMetaFactory.from(request));
    }
}

package com.fincore.search.api.controller;

import com.fincore.platform.api.ApiMetaFactory;
import com.fincore.platform.api.ApiResponse;
import com.fincore.platform.api.PageData;
import com.fincore.search.api.dto.response.SavingsAccountSearchResponse;
import com.fincore.search.application.service.SavingsAccountSearchQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class SavingsAccountSearchController {

    private final SavingsAccountSearchQueryService queryService;

    @GetMapping("/savings/accounts")
    @Operation(summary = "List savings accounts from Elasticsearch")
    public ApiResponse<PageData<SavingsAccountSearchResponse>> list(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String status,
            HttpServletRequest request) {
        return ApiResponse.success(queryService.list(limit, cursor, customerId, status),
                ApiMetaFactory.from(request));
    }
}

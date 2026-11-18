package com.fincore.search.api.controller;

import java.util.List;

import com.fincore.platform.api.ApiMetaFactory;
import com.fincore.platform.api.ApiResponse;
import com.fincore.search.api.dto.response.LoanActivityResponse;
import com.fincore.search.api.dto.response.LoanSearchResponse;
import com.fincore.search.application.service.LoanSearchQueryService;

import jakarta.servlet.http.HttpServletRequest;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class LoanSearchController {

    private final LoanSearchQueryService queryService;

    @GetMapping("/search/loans")
    public ApiResponse<List<LoanSearchResponse>> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String productCode,
            @RequestParam(defaultValue = "20") int limit,
            HttpServletRequest request) {
        return ApiResponse.success(queryService.search(query, status, productCode, limit),
                ApiMetaFactory.from(request));
    }

    @GetMapping("/loans/{loanId}/activity")
    public ResponseEntity<ApiResponse<LoanActivityResponse>> activity(
            @PathVariable String loanId, HttpServletRequest request) {
        var meta = ApiMetaFactory.from(request);
        return queryService.activity(loanId)
                .map(activity -> ResponseEntity.ok(ApiResponse.success(activity, meta)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.<LoanActivityResponse>failure(
                                new com.fincore.platform.api.ApiError(
                                        "PROJECTION_NOT_READY", "Loan activity projection is not ready", List.of()),
                                meta)));
    }
}

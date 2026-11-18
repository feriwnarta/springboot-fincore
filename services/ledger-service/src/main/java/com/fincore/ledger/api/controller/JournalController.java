package com.fincore.ledger.api.controller;

import com.fincore.ledger.api.dto.response.JournalResponse;
import com.fincore.ledger.api.mapper.JournalMapper;
import com.fincore.ledger.application.service.JournalQueryService;
import com.fincore.platform.api.ApiMetaFactory;
import com.fincore.platform.api.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/journals")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class JournalController {

    private final JournalQueryService queryService;

    @GetMapping("/by-business-ref/{reference}")
    public ResponseEntity<ApiResponse<JournalResponse>> findByBusinessReference(
            @PathVariable String reference,
            HttpServletRequest request) {
        var meta = ApiMetaFactory.from(request);
        var journal = queryService.findByBusinessTransactionReference(reference);
        return ResponseEntity.ok(ApiResponse.success(JournalMapper.toResponse(journal), meta));
    }
}

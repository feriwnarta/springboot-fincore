package com.fincore.ledger.api.controller;

import java.util.UUID;

import com.fincore.ledger.api.dto.request.OpenSavingsAccountRequest;
import com.fincore.ledger.api.dto.request.ChangeSavingsStatusRequest;
import com.fincore.ledger.api.dto.request.SavingsMoneyRequest;
import com.fincore.ledger.api.dto.response.SavingsAccountResponse;
import com.fincore.ledger.api.dto.response.SavingsStatementResponse;
import com.fincore.ledger.api.dto.response.SavingsTransactionResponse;
import com.fincore.ledger.api.mapper.SavingsMapper;
import com.fincore.ledger.application.command.OpenSavingsAccountCommand;
import com.fincore.ledger.application.command.ChangeSavingsStatusCommand;
import com.fincore.ledger.application.command.SavingsMoneyCommand;
import com.fincore.ledger.application.service.SavingsService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/savings/accounts")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class SavingsController {

    private static final String[] WRITE_ROLES = {"SAVINGS_OPERATOR", "LOAN_OPERATOR", "LOAN_APPROVER", "CUSTOMER"};
    private static final String[] STATUS_ROLES = {"SAVINGS_OPERATOR", "LOAN_OPERATOR", "LOAN_APPROVER"};

    private final SavingsService service;
    private final CurrentIdentity currentIdentity;

    @PostMapping
    public ResponseEntity<ApiResponse<SavingsAccountResponse>> open(
            @Valid @RequestBody OpenSavingsAccountRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest) {
        var actor = AuditActor.from(currentIdentity, WRITE_ROLES);
        var meta = ApiMetaFactory.from(httpRequest);
        var account = service.open(new OpenSavingsAccountCommand(request.customerId(), idempotencyKey, actor.id(),
                actor.role(), meta.correlationId(), meta.requestId()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(SavingsMapper.toResponse(account), meta));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<ApiResponse<SavingsAccountResponse>> get(
            @PathVariable UUID accountId, HttpServletRequest httpRequest) {
        var meta = ApiMetaFactory.from(httpRequest);
        return ResponseEntity.ok(ApiResponse.success(SavingsMapper.toResponse(service.getAccount(accountId)), meta));
    }

    @PostMapping("/{accountId}/status")
    public ResponseEntity<ApiResponse<SavingsAccountResponse>> changeStatus(
            @PathVariable UUID accountId,
            @Valid @RequestBody ChangeSavingsStatusRequest request,
            HttpServletRequest httpRequest) {
        var actor = AuditActor.from(currentIdentity, STATUS_ROLES);
        var meta = ApiMetaFactory.from(httpRequest);
        var account = service.changeStatus(new ChangeSavingsStatusCommand(accountId, request.status(), actor.id(),
                actor.role(), meta.correlationId(), meta.requestId()));
        return ResponseEntity.ok(ApiResponse.success(SavingsMapper.toResponse(account), meta));
    }

    @PostMapping("/{accountId}/deposits")
    public ResponseEntity<ApiResponse<SavingsTransactionResponse>> deposit(
            @PathVariable UUID accountId,
            @Valid @RequestBody SavingsMoneyRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest) {
        var actor = AuditActor.from(currentIdentity, WRITE_ROLES);
        var meta = ApiMetaFactory.from(httpRequest);
        var transaction = service.deposit(new SavingsMoneyCommand(accountId, request.amount(), idempotencyKey, actor.id(),
                actor.role(), meta.correlationId(), meta.requestId()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(SavingsMapper.toResponse(transaction), meta));
    }

    @PostMapping("/{accountId}/withdrawals")
    public ResponseEntity<ApiResponse<SavingsTransactionResponse>> withdraw(
            @PathVariable UUID accountId,
            @Valid @RequestBody SavingsMoneyRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest) {
        var actor = AuditActor.from(currentIdentity, WRITE_ROLES);
        var meta = ApiMetaFactory.from(httpRequest);
        var transaction = service.withdraw(new SavingsMoneyCommand(accountId, request.amount(), idempotencyKey, actor.id(),
                actor.role(), meta.correlationId(), meta.requestId()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(SavingsMapper.toResponse(transaction), meta));
    }

    @GetMapping("/{accountId}/statement")
    public ResponseEntity<ApiResponse<SavingsStatementResponse>> statement(
            @PathVariable UUID accountId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit,
            HttpServletRequest httpRequest) {
        var meta = ApiMetaFactory.from(httpRequest);
        var rows = service.statement(accountId, cursor, limit + 1);
        boolean hasNext = rows.size() > limit;
        var visible = hasNext ? rows.subList(0, limit) : rows;
        String nextCursor = hasNext
                ? visible.getLast().createdAt() + "|" + visible.getLast().id()
                : null;
        var response = new SavingsStatementResponse(visible.stream().map(SavingsMapper::toResponse).toList(), nextCursor);
        return ResponseEntity.ok(ApiResponse.success(response, meta));
    }
}

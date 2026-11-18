package com.fincore.ledger.application.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fincore.ledger.application.command.OpenSavingsAccountCommand;
import com.fincore.ledger.application.command.ChangeSavingsStatusCommand;
import com.fincore.ledger.application.command.SavingsMoneyCommand;
import com.fincore.ledger.domain.enums.IdempotencyStatus;
import com.fincore.ledger.domain.enums.JournalSide;
import com.fincore.ledger.domain.enums.JournalStatus;
import com.fincore.ledger.domain.enums.SavingsAccountStatus;
import com.fincore.ledger.domain.enums.SavingsOperationType;
import com.fincore.ledger.domain.model.JournalEntry;
import com.fincore.ledger.domain.model.JournalLine;
import com.fincore.ledger.domain.model.LedgerIdempotencyRecord;
import com.fincore.ledger.domain.model.LedgerOutboxEntry;
import com.fincore.ledger.domain.model.SavingsAccount;
import com.fincore.ledger.domain.model.SavingsTransaction;
import com.fincore.ledger.domain.port.JournalRepository;
import com.fincore.ledger.domain.port.LedgerAuditRepository;
import com.fincore.ledger.domain.port.LedgerIdempotencyRepository;
import com.fincore.ledger.domain.port.LedgerOutboxRepository;
import com.fincore.ledger.domain.port.SavingsRepository;
import com.fincore.platform.audit.AuditEvent;
import com.fincore.platform.security.CurrentIdentity;
import com.fincore.platform.web.ConflictException;
import com.fincore.platform.web.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SavingsService {

    private static final String CURRENCY = "IDR";
    private static final String OPEN_OPERATION = "OPEN_SAVINGS_ACCOUNT";
    private static final String DEPOSIT_OPERATION = "SAVINGS_DEPOSIT";
    private static final String WITHDRAW_OPERATION = "SAVINGS_WITHDRAWAL";
    private static final String[] STAFF_WRITE_ROLES = {"SAVINGS_OPERATOR", "LOAN_OPERATOR", "LOAN_APPROVER"};

    private final SavingsRepository savingsRepository;
    private final JournalRepository journalRepository;
    private final LedgerAuditRepository auditRepository;
    private final LedgerIdempotencyRepository idempotencyRepository;
    private final LedgerOutboxRepository outboxRepository;
    private final CurrentIdentity currentIdentity;

    @Transactional
    public SavingsAccount open(OpenSavingsAccountCommand command) {
        currentIdentity.requireCustomerAccess(command.customerId(), STAFF_WRITE_ROLES);
        requireText(command.customerId(), "customerId");
        LedgerIdempotencyRecord requested = idempotency(command.customerId(), OPEN_OPERATION,
                command.idempotencyKey(), command.customerId());
        if (!idempotencyRepository.createIfAbsent(requested)) {
            LedgerIdempotencyRecord existing = existing(requested);
            if (!existing.requestFingerprint().equals(requested.requestFingerprint())) {
                throw new ConflictException("Idempotency key was already used with a different request");
            }
            if (existing.resultReference() == null) {
                throw new ConflictException("Idempotency request is being processed");
            }
            UUID accountId;
            try {
                accountId = UUID.fromString(existing.resultReference());
            } catch (IllegalArgumentException exception) {
                throw new ConflictException("Idempotency result is unavailable");
            }
            SavingsAccount account = savingsRepository.findAccountById(accountId)
                    .orElseThrow(() -> new ConflictException("Idempotency result is unavailable"));
            if (!command.customerId().equals(account.customerId())) {
                throw new AccessDeniedException("Customer access denied");
            }
            return account;
        }

        Instant now = Instant.now();
        SavingsAccount account = new SavingsAccount(UUID.randomUUID(), "SAV-" + UUID.randomUUID(),
                command.customerId(), CURRENCY, SavingsAccountStatus.ACTIVE, BigDecimal.ZERO, 1, now, now);
        savingsRepository.saveAccount(account);
        audit(account.id(), 1, "OPEN", command, account.accountNumber(), Map.of("currency", CURRENCY));
        appendAccountOutbox(account, "SavingsAccountOpened", account.currentBalance(), now, account.accountNumber());
        idempotencyRepository.markSucceeded(requested, account.id().toString());
        return account;
    }

    @Transactional
    public SavingsTransaction deposit(SavingsMoneyCommand command) {
        return move(command, SavingsOperationType.DEPOSIT);
    }

    @Transactional
    public SavingsTransaction withdraw(SavingsMoneyCommand command) {
        return move(command, SavingsOperationType.WITHDRAWAL);
    }

    public SavingsAccount getAccount(UUID accountId) {
        return loadAccessibleAccount(accountId, "SAVINGS_OPERATOR");
    }

    private SavingsAccount loadAccessibleAccount(UUID accountId, String... operatorRoles) {
        SavingsAccount account = loadAccount(accountId);
        currentIdentity.requireCustomerAccess(account.customerId(), operatorRoles);
        return account;
    }

    private SavingsAccount loadAccount(UUID accountId) {
        return savingsRepository.findAccountById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Savings account was not found"));
    }

    @Transactional
    public SavingsAccount changeStatus(ChangeSavingsStatusCommand command) {
        SavingsAccount current = loadAccessibleAccount(command.accountId(), STAFF_WRITE_ROLES);
        if (Arrays.stream(STAFF_WRITE_ROLES).noneMatch(currentIdentity::hasRole)) {
            throw new AccessDeniedException("Access denied");
        }
        if (current.status() == SavingsAccountStatus.CLOSED) {
            throw new ConflictException("Closed savings account cannot change status");
        }
        if (!savingsRepository.updateStatus(current.id(), command.status())) {
            throw new ConflictException("Savings account status was changed by another request");
        }
        auditRepository.append(new AuditEvent(UUID.randomUUID(), "SavingsAccount", current.id().toString(),
                current.version() + 1, "STATUS_" + command.status().name(), "SUCCESS", command.actorId(),
                command.actorRole(), command.correlationId(), command.requestId(), current.accountNumber(),
                Map.of("status", command.status().name()), Instant.now()));
        SavingsAccount updated = loadAccount(current.id());
        appendAccountOutbox(updated, "SavingsAccountStatusChanged", updated.currentBalance(), updated.updatedAt(),
                updated.accountNumber());
        return updated;
    }

    public List<SavingsTransaction> statement(UUID accountId, String cursor, int limit) {
        getAccount(accountId);
        if (limit < 1 || limit > 101) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        return savingsRepository.findTransactions(accountId, cursor, limit);
    }

    private SavingsTransaction move(SavingsMoneyCommand command, SavingsOperationType operation) {
        loadAccessibleAccount(command.accountId(), STAFF_WRITE_ROLES);
        validateAmount(command.amount());
        String operationName = operation == SavingsOperationType.DEPOSIT ? DEPOSIT_OPERATION : WITHDRAW_OPERATION;
        String scope = command.accountId().toString();
        LedgerIdempotencyRecord requested = idempotency(scope, operationName, command.idempotencyKey(),
                command.amount().toPlainString());
        if (!idempotencyRepository.createIfAbsent(requested)) {
            LedgerIdempotencyRecord existing = existing(requested);
            if (!existing.requestFingerprint().equals(requested.requestFingerprint())) {
                throw new ConflictException("Idempotency key was already used with a different request");
            }
            if (existing.resultReference() == null) {
                throw new ConflictException("Idempotency request is being processed");
            }
            SavingsTransaction transaction = savingsRepository.findTransactionByBusinessReference(existing.resultReference())
                    .orElseThrow(() -> new ConflictException("Idempotency result is unavailable"));
            if (!command.accountId().equals(transaction.accountId())) {
                throw new AccessDeniedException("Customer access denied");
            }
            if (operation != transaction.operationType()) {
                throw new ConflictException("Idempotency result is unavailable");
            }
            return transaction;
        }

        SavingsAccount account = savingsRepository.findAccountByIdForUpdate(command.accountId())
                .orElseThrow(() -> new ResourceNotFoundException("Savings account was not found"));
        if (account.status() != SavingsAccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Savings account is not active");
        }
        BigDecimal nextBalance = operation == SavingsOperationType.DEPOSIT
                ? account.currentBalance().add(command.amount())
                : account.currentBalance().subtract(command.amount());
        if (nextBalance.signum() < 0) {
            throw new IllegalArgumentException("Savings account has insufficient balance");
        }
        if (!savingsRepository.updateBalance(account.id(), account.version(), nextBalance)) {
            throw new ConflictException("Savings account was changed by another request");
        }

        String businessReference = "SAV-" + operation.name() + "-" + UUID.randomUUID();
        UUID journalId = UUID.randomUUID();
        JournalEntry journal = journal(account, journalId, businessReference, operation, command.amount());
        journalRepository.save(journal);
        Instant now = Instant.now();
        SavingsTransaction transaction = new SavingsTransaction(UUID.randomUUID(), account.id(), businessReference,
                operation, command.amount(), nextBalance, journalId, now);
        savingsRepository.saveTransaction(transaction);
        audit(account.id(), account.version() + 1, operation.name(), command, businessReference,
                Map.of("amount", command.amount().toPlainString(), "balanceAfter", nextBalance.toPlainString()));
        SavingsAccount updated = new SavingsAccount(account.id(), account.accountNumber(), account.customerId(),
                account.currency(), account.status(), nextBalance, account.version() + 1, account.createdAt(), now);
        appendOutbox(updated, journal, operation, command, now);
        idempotencyRepository.markSucceeded(requested, businessReference);
        return transaction;
    }

    private static JournalEntry journal(SavingsAccount account, UUID journalId, String reference,
            SavingsOperationType operation, BigDecimal amount) {
        JournalLine first = operation == SavingsOperationType.DEPOSIT
                ? new JournalLine(UUID.randomUUID(), "SIMULATED_CASH_CLEARING", JournalSide.DEBIT, amount, CURRENCY)
                : new JournalLine(UUID.randomUUID(), "SAVINGS_LIABILITY", JournalSide.DEBIT, amount, CURRENCY);
        JournalLine second = operation == SavingsOperationType.DEPOSIT
                ? new JournalLine(UUID.randomUUID(), "SAVINGS_LIABILITY", JournalSide.CREDIT, amount, CURRENCY)
                : new JournalLine(UUID.randomUUID(), "SIMULATED_CASH_CLEARING", JournalSide.CREDIT, amount, CURRENCY);
        return new JournalEntry(journalId, reference, JournalStatus.POSTED, account.currency(), List.of(first, second));
    }

    private void appendOutbox(SavingsAccount account, JournalEntry journal, SavingsOperationType operation,
            SavingsMoneyCommand command, Instant now) {
        UUID eventId = UUID.randomUUID();
        String eventType = operation == SavingsOperationType.DEPOSIT ? "SavingsDepositPosted" : "SavingsWithdrawalPosted";
        outboxRepository.append(new LedgerOutboxEntry(eventId, account.id(), account.version(), eventType, 1,
                journal.businessTransactionReference(), "{\"eventId\":\"" + eventId + "\",\"eventType\":\""
                        + eventType + "\",\"schemaVersion\":1,\"aggregateId\":\"" + account.id()
                        + "\",\"aggregateVersion\":" + account.version() + ",\"correlationId\":\""
                        + json(command.correlationId()) + "\",\"businessReference\":\""
                        + journal.businessTransactionReference() + "\",\"payload\":{\"accountId\":\""
                        + account.id() + "\",\"amount\":\"" + journal.lines().getFirst().amount().toPlainString()
                        + "\",\"currency\":\"IDR\",\"accountNumber\":\"" + account.accountNumber()
                        + "\",\"customerId\":\"" + account.customerId() + "\",\"status\":\""
                        + account.status().name() + "\",\"currentBalance\":\"" + account.currentBalance()
                        + "\",\"createdAt\":\"" + account.createdAt() + "\",\"updatedAt\":\"" + now
                        + "\"}}", now));
    }

    private void appendAccountOutbox(SavingsAccount account, String eventType, BigDecimal balance, Instant now,
            String businessReference) {
        UUID eventId = UUID.randomUUID();
        outboxRepository.append(new LedgerOutboxEntry(eventId, account.id(), account.version(), eventType, 1,
                businessReference, "{\"eventId\":\"" + eventId + "\",\"eventType\":\"" + eventType
                        + "\",\"schemaVersion\":1,\"aggregateId\":\"" + account.id()
                        + "\",\"aggregateVersion\":" + account.version() + ",\"payload\":{\"accountNumber\":\""
                        + account.accountNumber() + "\",\"customerId\":\"" + account.customerId()
                        + "\",\"currency\":\"" + account.currency() + "\",\"status\":\""
                        + account.status().name() + "\",\"currentBalance\":\"" + balance
                        + "\",\"createdAt\":\"" + account.createdAt() + "\",\"updatedAt\":\"" + now
                        + "\"}}", now));
    }

    private void audit(UUID accountId, long version, String action, OpenSavingsAccountCommand command,
            String businessReference, Map<String, Object> metadata) {
        auditRepository.append(new AuditEvent(UUID.randomUUID(), "SavingsAccount", accountId.toString(), version,
                action, "SUCCESS", command.actorId(), command.actorRole(), command.correlationId(), command.requestId(),
                businessReference, metadata, Instant.now()));
    }

    private void audit(UUID accountId, long version, String action, SavingsMoneyCommand command,
            String businessReference, Map<String, Object> metadata) {
        auditRepository.append(new AuditEvent(UUID.randomUUID(), "SavingsAccount", accountId.toString(), version,
                action, "SUCCESS", command.actorId(), command.actorRole(), command.correlationId(), command.requestId(),
                businessReference, metadata, Instant.now()));
    }

    private static LedgerIdempotencyRecord idempotency(String scope, String operation, String key, String fingerprintInput) {
        requireText(key, "idempotencyKey");
        return new LedgerIdempotencyRecord(scope, operation, key, fingerprint(fingerprintInput), null,
                IdempotencyStatus.IN_PROGRESS);
    }

    private LedgerIdempotencyRecord existing(LedgerIdempotencyRecord requested) {
        return idempotencyRepository.findByKey(requested.callerScope(), requested.operation(), requested.idempotencyKey())
                .orElseThrow(() -> new ConflictException("Idempotency request is being processed"));
    }

    private static String fingerprint(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

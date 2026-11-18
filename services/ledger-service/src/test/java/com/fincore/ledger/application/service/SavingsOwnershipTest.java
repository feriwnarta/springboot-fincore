package com.fincore.ledger.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.fincore.ledger.application.command.ChangeSavingsStatusCommand;
import com.fincore.ledger.application.command.OpenSavingsAccountCommand;
import com.fincore.ledger.application.command.SavingsMoneyCommand;
import com.fincore.ledger.domain.enums.IdempotencyStatus;
import com.fincore.ledger.domain.enums.SavingsAccountStatus;
import com.fincore.ledger.domain.enums.SavingsOperationType;
import com.fincore.ledger.domain.model.LedgerIdempotencyRecord;
import com.fincore.ledger.domain.model.SavingsAccount;
import com.fincore.ledger.domain.model.SavingsTransaction;
import com.fincore.ledger.domain.port.JournalRepository;
import com.fincore.ledger.domain.port.LedgerAuditRepository;
import com.fincore.ledger.domain.port.LedgerIdempotencyRepository;
import com.fincore.ledger.domain.port.LedgerOutboxRepository;
import com.fincore.ledger.domain.port.SavingsRepository;
import com.fincore.platform.security.SpringCurrentIdentity;
import com.fincore.platform.web.ConflictException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class SavingsOwnershipTest {

    private static final String OWNER = "customer-001";
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final SavingsAccount ACCOUNT = new SavingsAccount(ACCOUNT_ID, "SAV-001", OWNER, "IDR",
            SavingsAccountStatus.ACTIVE, BigDecimal.TEN, 1, Instant.EPOCH, Instant.EPOCH);

    private final SavingsRepository accounts = mock(SavingsRepository.class);
    private final JournalRepository journal = mock(JournalRepository.class);
    private final LedgerAuditRepository audit = mock(LedgerAuditRepository.class);
    private final LedgerIdempotencyRepository idempotency = mock(LedgerIdempotencyRepository.class);
    private final LedgerOutboxRepository outbox = mock(LedgerOutboxRepository.class);

    private final SavingsService service = new SavingsService(
            accounts, journal, audit, idempotency, outbox, new SpringCurrentIdentity());

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void customerWithDifferentCaseCannotAccessAccount(Operation operation) {
        authenticate("CUSTOMER-001", "CUSTOMER");
        assertDeniedBeforeSideEffects(operation);
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void customerWithoutOwnershipClaimCannotAccessAccount(Operation operation) {
        authenticate(null, "CUSTOMER");
        assertDeniedBeforeSideEffects(operation);
    }

    @ParameterizedTest
    @EnumSource(value = Operation.class, names = "STATUS", mode = EnumSource.Mode.EXCLUDE)
    void customerCanUseOwnAccount(Operation operation) {
        authenticate(OWNER, "CUSTOMER");
        assertOperationSucceeds(operation);
    }

    @Test
    void matchingCustomerCannotChangeStatus() {
        authenticate(OWNER, "CUSTOMER");
        assertDeniedBeforeSideEffects(Operation.STATUS);
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void savingsOperatorCanUseAnotherCustomersAccount(Operation operation) {
        authenticate("customer-002", "SAVINGS_OPERATOR");
        assertOperationSucceeds(operation);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LOAN_OPERATOR", "LOAN_APPROVER"})
    void loanStaffKeepOperationalWritesButCannotUseSavingsReads(String role) {
        authenticate("customer-002", role);
        assertDeniedBeforeSideEffects(Operation.READ);
        clearInvocations(accounts);
        assertDeniedBeforeSideEffects(Operation.STATEMENT);
        assertOperationSucceeds(Operation.OPEN);
        assertOperationSucceeds(Operation.DEPOSIT);
        assertOperationSucceeds(Operation.WITHDRAW);
        assertOperationSucceeds(Operation.STATUS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "SAVINGS_OPERATOR"})
    void openReplayCannotReturnAnAccountForADifferentCustomer(String role) {
        authenticate(OWNER, role);
        var otherAccount = new SavingsAccount(ACCOUNT_ID, "SAV-001", "CUSTOMER-001", "IDR",
                SavingsAccountStatus.ACTIVE, BigDecimal.TEN, 1, Instant.EPOCH, Instant.EPOCH);
        stubReplay(OWNER, "OPEN_SAVINGS_ACCOUNT", otherAccount.id().toString());
        when(accounts.findAccountById(otherAccount.id())).thenReturn(Optional.of(otherAccount));

        assertThatThrownBy(() -> invoke(Operation.OPEN)).isInstanceOf(AccessDeniedException.class);

        verify(accounts).findAccountById(otherAccount.id());
        verifyNoMoreInteractions(accounts);
        verifyNoInteractions(journal, audit, outbox);
    }

    @Test
    void openReplayWithMalformedReferenceReturnsSanitizedConflict() {
        authenticate(OWNER, "CUSTOMER");
        stubReplay(OWNER, "OPEN_SAVINGS_ACCOUNT", "invalid-stored-reference");

        assertThatThrownBy(() -> invoke(Operation.OPEN))
                .isInstanceOf(ConflictException.class).hasMessage("Idempotency result is unavailable");

        verifyNoInteractions(accounts, journal, audit, outbox);
    }

    @ParameterizedTest
    @CsvSource({"DEPOSIT, DEPOSIT", "WITHDRAW, WITHDRAWAL"})
    void movementReplayCannotReturnATransactionForAnotherAccount(Operation operation, SavingsOperationType type) {
        authenticate(OWNER, "CUSTOMER");
        when(accounts.findAccountById(ACCOUNT_ID)).thenReturn(Optional.of(ACCOUNT));
        String reference = "SAV-" + type.name() + "-" + UUID.randomUUID();
        stubReplay(ACCOUNT_ID.toString(), "SAVINGS_" + type.name(), reference);
        var otherTransaction = transaction(UUID.randomUUID(), reference, type);
        when(accounts.findTransactionByBusinessReference(reference)).thenReturn(Optional.of(otherTransaction));

        assertThatThrownBy(() -> invoke(operation)).isInstanceOf(AccessDeniedException.class);

        verify(accounts).findAccountById(ACCOUNT_ID);
        verify(accounts).findTransactionByBusinessReference(reference);
        verifyNoMoreInteractions(accounts);
        verifyNoInteractions(journal, audit, outbox);
    }

    @Test
    void movementReplayRejectsAReferenceForTheWrongOperationOnTheSameAccount() {
        authenticate(OWNER, "CUSTOMER");
        when(accounts.findAccountById(ACCOUNT_ID)).thenReturn(Optional.of(ACCOUNT));
        String reference = "SAV-WITHDRAWAL-" + UUID.randomUUID();
        stubReplay(ACCOUNT_ID.toString(), "SAVINGS_DEPOSIT", reference);
        when(accounts.findTransactionByBusinessReference(reference))
                .thenReturn(Optional.of(transaction(ACCOUNT_ID, reference, SavingsOperationType.WITHDRAWAL)));

        assertThatThrownBy(() -> invoke(Operation.DEPOSIT))
                .isInstanceOf(ConflictException.class).hasMessage("Idempotency result is unavailable");
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "nonexistent-reference"})
    void movementReplayRejectsAnUnavailableReference(String reference) {
        authenticate(OWNER, "CUSTOMER");
        when(accounts.findAccountById(ACCOUNT_ID)).thenReturn(Optional.of(ACCOUNT));
        stubReplay(ACCOUNT_ID.toString(), "SAVINGS_DEPOSIT", reference);

        assertThatThrownBy(() -> invoke(Operation.DEPOSIT))
                .isInstanceOf(ConflictException.class).hasMessage("Idempotency result is unavailable");
    }

    private static SavingsTransaction transaction(UUID accountId, String reference, SavingsOperationType type) {
        return new SavingsTransaction(UUID.randomUUID(), accountId, reference, type, BigDecimal.ONE, BigDecimal.TEN,
                UUID.randomUUID(), Instant.EPOCH);
    }

    private void stubReplay(String scope, String operation, String resultReference) {
        var request = new AtomicReference<LedgerIdempotencyRecord>();
        when(idempotency.createIfAbsent(any())).thenAnswer(invocation -> {
            request.set(invocation.getArgument(0));
            return false;
        });
        when(idempotency.findByKey(scope, operation, "known-key")).thenAnswer(invocation -> {
            LedgerIdempotencyRecord requested = request.get();
            return Optional.of(new LedgerIdempotencyRecord(requested.callerScope(), requested.operation(),
                    requested.idempotencyKey(), requested.requestFingerprint(), resultReference,
                    IdempotencyStatus.SUCCEEDED));
        });
    }

    private void assertDeniedBeforeSideEffects(Operation operation) {
        if (operation != Operation.OPEN) {
            when(accounts.findAccountById(ACCOUNT_ID)).thenReturn(Optional.of(ACCOUNT));
        }
        assertThatThrownBy(() -> invoke(operation)).isInstanceOf(AccessDeniedException.class);
        if (operation != Operation.OPEN) {
            verify(accounts).findAccountById(ACCOUNT_ID);
        }
        verifyNoMoreInteractions(accounts);
        verifyNoInteractions(idempotency, journal, audit, outbox);
    }

    private void assertOperationSucceeds(Operation operation) {
        when(accounts.findAccountById(ACCOUNT_ID)).thenReturn(Optional.of(ACCOUNT));
        when(accounts.findAccountByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(ACCOUNT));
        when(accounts.updateBalance(eq(ACCOUNT_ID), eq(1L), any())).thenReturn(true);
        when(accounts.updateStatus(ACCOUNT_ID, SavingsAccountStatus.FROZEN)).thenReturn(true);
        when(idempotency.createIfAbsent(any())).thenReturn(true);

        switch (operation) {
            case OPEN -> assertThat(invoke(operation)).isInstanceOfSatisfying(SavingsAccount.class,
                    result -> assertThat(result.customerId()).isEqualTo(OWNER));
            case READ, STATUS -> assertThat(invoke(operation)).isEqualTo(ACCOUNT);
            case STATEMENT -> {
                assertThat(invoke(operation)).isEqualTo(List.of());
                verify(accounts).findTransactions(ACCOUNT_ID, null, 10);
            }
            case DEPOSIT, WITHDRAW -> assertThat(invoke(operation)).isInstanceOfSatisfying(
                    SavingsTransaction.class,
                    result -> assertThat(result.balanceAfter()).isEqualByComparingTo(
                            operation == Operation.DEPOSIT ? "11" : "9"));
        }
    }

    private Object invoke(Operation operation) {
        var money = new SavingsMoneyCommand(ACCOUNT_ID, BigDecimal.ONE, "known-key", "actor", "role", "corr", "req");
        return switch (operation) {
            case OPEN -> service.open(new OpenSavingsAccountCommand(OWNER, "known-key", "actor", "role", "corr", "req"));
            case READ -> service.getAccount(ACCOUNT_ID);
            case STATEMENT -> service.statement(ACCOUNT_ID, null, 10);
            case DEPOSIT -> service.deposit(money);
            case WITHDRAW -> service.withdraw(money);
            case STATUS -> service.changeStatus(new ChangeSavingsStatusCommand(
                    ACCOUNT_ID, SavingsAccountStatus.FROZEN, "actor", "role", "corr", "req"));
        };
    }

    private static void authenticate(String customerId, String... roles) {
        var token = Jwt.withTokenValue("token").header("alg", "RS256").subject("subject");
        if (customerId != null) {
            token.claim("customer_id", customerId);
        }
        var authorities = Arrays.stream(roles).map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token.build(), authorities));
    }

    private enum Operation { OPEN, READ, STATEMENT, DEPOSIT, WITHDRAW, STATUS }
}

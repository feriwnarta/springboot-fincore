package com.fincore.ledger.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import com.fincore.ledger.LedgerServiceApplication;
import com.fincore.ledger.application.command.OpenSavingsAccountCommand;
import com.fincore.ledger.application.command.SavingsMoneyCommand;
import com.fincore.ledger.domain.port.LedgerIdempotencyRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(classes = LedgerServiceApplication.class)
@ActiveProfiles("test")
class SavingsServiceIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.4-alpine")
            .withCreateContainerCmdModifier(command -> command.withPlatform("linux/amd64"));

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.kafka.listener.auto-startup", () -> false);
    }

    @Autowired
    private SavingsService savingsService;

    @Autowired
    private LedgerIdempotencyRepository idempotencyRepository;

    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void twoConcurrentWithdrawalsCannotOverdrawSharedBalance() throws Exception {
        authenticate("SAVINGS_OPERATOR", null);
        String customerId = "customer-" + UUID.randomUUID();
        var account = savingsService.open(new OpenSavingsAccountCommand(customerId, "open-" + UUID.randomUUID(),
                "test", "operator", UUID.randomUUID().toString(), UUID.randomUUID().toString()));
        savingsService.deposit(new SavingsMoneyCommand(account.id(), new BigDecimal("100.0000"),
                "deposit-" + UUID.randomUUID(), "test", "operator", UUID.randomUUID().toString(),
                UUID.randomUUID().toString()));

        Callable<Boolean> withdrawal = () -> {
            authenticate("SAVINGS_OPERATOR", null);
            try {
                savingsService.withdraw(new SavingsMoneyCommand(account.id(), new BigDecimal("80.0000"),
                        "withdraw-" + UUID.randomUUID(), "test", "operator", UUID.randomUUID().toString(),
                        UUID.randomUUID().toString()));
                return true;
            } catch (IllegalArgumentException exception) {
                assertThat(exception).hasMessage("Savings account has insufficient balance");
                return false;
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(List.of(withdrawal, withdrawal)).stream()
                    .map(future -> getUnchecked(future)).toList();
            assertThat(results).containsExactlyInAnyOrder(true, false);
        }

        assertThat(savingsService.getAccount(account.id()).currentBalance()).isEqualByComparingTo("20.0000");
    }

    @Test
    void ownershipIsRequiredForBothNewAndReplayedFinancialCommands() {
        String owner = "customer-" + UUID.randomUUID();
        authenticate("CUSTOMER", owner);
        var open = new OpenSavingsAccountCommand(owner, "open", "test", "CUSTOMER", "corr", "req");
        var account = savingsService.open(open);
        assertThat(savingsService.open(open).id()).isEqualTo(account.id());

        var deposit = new SavingsMoneyCommand(account.id(), new BigDecimal("100.0000"),
                "deposit", "test", "CUSTOMER", "corr", "req");
        var transaction = savingsService.deposit(deposit);
        assertThat(savingsService.deposit(deposit).id()).isEqualTo(transaction.id());

        authenticate("CUSTOMER", "other-" + UUID.randomUUID());
        var withdraw = new SavingsMoneyCommand(account.id(), BigDecimal.ONE, "withdraw", "test", "CUSTOMER",
                "corr", "req");
        assertThatThrownBy(() -> savingsService.open(open)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> savingsService.deposit(deposit)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> savingsService.withdraw(withdraw)).isInstanceOf(AccessDeniedException.class);
        assertThat(idempotencyRepository.findByKey(account.id().toString(), "SAVINGS_WITHDRAWAL", "withdraw"))
                .isEmpty();

        authenticate("CUSTOMER", owner);
        assertThat(savingsService.getAccount(account.id()).currentBalance()).isEqualByComparingTo("100.0000");
        assertThat(savingsService.statement(account.id(), null, 20))
                .extracting(row -> row.id()).containsExactly(transaction.id());
    }

    @Test
    void replayRejectsReferencesToAnotherCustomersTransactions() {
        String owner = "customer-" + UUID.randomUUID();
        String otherOwner = "customer-" + UUID.randomUUID();
        authenticate("SAVINGS_OPERATOR", null);
        var account = savingsService.open(new OpenSavingsAccountCommand(
                owner, "open", "test", "SAVINGS_OPERATOR", "corr", "req"));
        var otherAccount = savingsService.open(new OpenSavingsAccountCommand(
                otherOwner, "open", "test", "SAVINGS_OPERATOR", "corr", "req"));

        var deposit = new SavingsMoneyCommand(account.id(), BigDecimal.TEN, "deposit", "test", "CUSTOMER",
                "corr", "req");
        var withdraw = new SavingsMoneyCommand(account.id(), BigDecimal.ONE, "withdraw", "test", "CUSTOMER",
                "corr", "req");
        var otherDeposit = savingsService.deposit(new SavingsMoneyCommand(
                otherAccount.id(), deposit.amount(), "deposit", "test", "SAVINGS_OPERATOR", "corr", "req"));
        var otherWithdrawal = savingsService.withdraw(new SavingsMoneyCommand(
                otherAccount.id(), withdraw.amount(), "withdraw", "test", "SAVINGS_OPERATOR", "corr", "req"));

        authenticate("CUSTOMER", owner);
        var originalDeposit = savingsService.deposit(deposit);
        var originalWithdrawal = savingsService.withdraw(withdraw);
        var depositRecord = idempotencyRepository.findByKey(account.id().toString(), "SAVINGS_DEPOSIT", "deposit")
                .orElseThrow();
        var withdrawalRecord = idempotencyRepository.findByKey(account.id().toString(), "SAVINGS_WITHDRAWAL", "withdraw")
                .orElseThrow();
        idempotencyRepository.markSucceeded(depositRecord, otherDeposit.businessReference());
        idempotencyRepository.markSucceeded(withdrawalRecord, otherWithdrawal.businessReference());

        assertThatThrownBy(() -> savingsService.deposit(deposit)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> savingsService.withdraw(withdraw)).isInstanceOf(AccessDeniedException.class);

        idempotencyRepository.markSucceeded(depositRecord, originalDeposit.businessReference());
        idempotencyRepository.markSucceeded(withdrawalRecord, originalWithdrawal.businessReference());
        assertThat(savingsService.deposit(deposit).id()).isEqualTo(originalDeposit.id());
        assertThat(savingsService.withdraw(withdraw).id()).isEqualTo(originalWithdrawal.id());
        assertThat(savingsService.getAccount(account.id()).currentBalance()).isEqualByComparingTo("9.0000");
        assertThat(savingsService.statement(account.id(), null, 20))
                .extracting(row -> row.id()).containsExactlyInAnyOrder(originalDeposit.id(), originalWithdrawal.id());
    }

    private static void authenticate(String role, String customerId) {
        var token = Jwt.withTokenValue("test").header("alg", "RS256").subject("test");
        if (customerId != null) {
            token.claim("customer_id", customerId);
        }
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                token.build(), List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private static Boolean getUnchecked(java.util.concurrent.Future<Boolean> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}

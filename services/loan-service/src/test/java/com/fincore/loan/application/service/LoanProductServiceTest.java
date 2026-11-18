package com.fincore.loan.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fincore.loan.application.command.CreateLoanProductCommand;
import com.fincore.loan.application.query.LoanProductPage;
import com.fincore.loan.domain.enums.InterestType;
import com.fincore.loan.domain.model.IdempotencyRecord;
import com.fincore.loan.domain.model.LoanProduct;
import com.fincore.loan.domain.port.LoanAuditRepository;
import com.fincore.loan.domain.port.LoanIdempotencyRepository;
import com.fincore.loan.domain.port.LoanProductRepository;
import com.fincore.platform.security.CurrentIdentity;
import com.fincore.platform.web.ConflictException;

import org.junit.jupiter.api.Test;

class LoanProductServiceTest {

    @Test
    void returnsOnlyTheRequestedPageAndAnOpaqueCursorForTheNextPage() {
        LoanProductRepository repository = mock(LoanProductRepository.class);
        LoanProduct first = product("PERSONAL_FLAT");
        LoanProduct second = product("PERSONAL_ANNUITY");
        LoanProduct third = product("BUSINESS_FLAT");
        when(repository.findActiveAfter(null, 3)).thenReturn(List.of(first, second, third));

        LoanProductPage page = service(repository).list(2, null);

        assertThat(page.items()).containsExactly(first, second);
        assertThat(page.hasNext()).isTrue();
        assertThat(page.nextCursor()).isNotEqualTo(second.code());
        assertThat(service(repository).decodeCursor(page.nextCursor()))
                .isEqualTo(second.code());
    }

    @Test
    void createsProductPersistsAuditAndCompletesIdempotency() {
        LoanProductRepository repository = mock(LoanProductRepository.class);
        LoanAuditRepository audit = mock(LoanAuditRepository.class);
        LoanIdempotencyRepository idempotency = mock(LoanIdempotencyRepository.class);
        CurrentIdentity identity = mock(CurrentIdentity.class);
        when(identity.hasRole("LOAN_PRODUCT_ADMIN")).thenReturn(true);
        when(identity.subject()).thenReturn("product-admin");
        when(idempotency.createIfAbsent(org.mockito.ArgumentMatchers.any())).thenReturn(true);

        LoanProductService service = new LoanProductService(repository, audit, idempotency, identity);
        LoanProduct created = service.create(new CreateLoanProductCommand(
                "KTA_ANNUITY", InterestType.ANNUITY, new BigDecimal("0.120000"),
                new BigDecimal("1000000.0000"), new BigDecimal("50000000.0000"), true,
                "product-create-001", "corr-001", "req-001"));

        assertThat(created.code()).isEqualTo("KTA_ANNUITY");
        assertThat(created.version()).isEqualTo(1);
        verify(repository).save(created);
        verify(audit).append(org.mockito.ArgumentMatchers.any());
        verify(idempotency).markSucceeded(org.mockito.ArgumentMatchers.any(IdempotencyRecord.class),
                org.mockito.ArgumentMatchers.eq(created.id().toString()));
    }

    @Test
    void rejectsDuplicateProductCode() {
        LoanProductRepository repository = mock(LoanProductRepository.class);
        LoanAuditRepository audit = mock(LoanAuditRepository.class);
        LoanIdempotencyRepository idempotency = mock(LoanIdempotencyRepository.class);
        CurrentIdentity identity = mock(CurrentIdentity.class);
        when(identity.hasRole("LOAN_PRODUCT_ADMIN")).thenReturn(true);
        when(identity.subject()).thenReturn("product-admin");
        when(repository.findByCode("KTA_ANNUITY")).thenReturn(java.util.Optional.of(product("KTA_ANNUITY")));

        LoanProductService service = new LoanProductService(repository, audit, idempotency, identity);

        assertThatThrownBy(() -> service.create(new CreateLoanProductCommand(
                "KTA_ANNUITY", InterestType.ANNUITY, new BigDecimal("0.120000"),
                new BigDecimal("1000000.0000"), new BigDecimal("50000000.0000"), true,
                "product-create-002", "corr-001", "req-002")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Loan product code already exists");
    }

    private static LoanProductService service(LoanProductRepository repository) {
        return new LoanProductService(repository, mock(LoanAuditRepository.class),
                mock(LoanIdempotencyRepository.class), mock(CurrentIdentity.class));
    }

    private static LoanProduct product(String code) {
        return new LoanProduct(
                UUID.randomUUID(),
                code,
                InterestType.FLAT,
                new BigDecimal("0.120000"),
                new BigDecimal("1000000.0000"),
                new BigDecimal("50000000.0000"),
                true,
                1,
                Instant.parse("2026-11-17T03:00:00Z"));
    }
}

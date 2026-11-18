package com.fincore.search.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.fincore.platform.security.SpringCurrentIdentity;
import com.fincore.search.api.dto.response.LoanSearchResponse;
import com.fincore.search.infrastructure.search.LoanActivityDocument;
import com.fincore.search.infrastructure.search.LoanActivityDocumentRepository;
import com.fincore.search.infrastructure.search.LoanSearchDocument;
import com.fincore.search.infrastructure.search.LoanSearchDocumentRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class LoanSearchQueryServiceTest {

    private static final String OWNER = "customer-001";
    private static final PageRequest PAGE = PageRequest.of(0, 20);

    private final LoanSearchDocumentRepository searchRepository = mock(LoanSearchDocumentRepository.class);
    private final LoanActivityDocumentRepository activityRepository = mock(LoanActivityDocumentRepository.class);

    private final LoanSearchQueryService service = new LoanSearchQueryService(
            searchRepository, activityRepository, new SpringCurrentIdentity());

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @CsvSource({
            ", , ",
            ", FUNDED, ",
            ", , P-1",
            ", FUNDED, P-1",
            "loan, , ",
            "loan, FUNDED, ",
            "loan, , P-1",
            "loan, FUNDED, P-1"
    })
    void allCustomerSearchVariantsUseAnExactOwnershipConstraint(String query, String status, String productCode) {
        authenticate("CUSTOMER", OWNER);
        when(searchRepository.findMatching(Optional.of(OWNER), query, status, productCode, PAGE)).thenReturn(List.of(
                loan("own", OWNER), loan("other", "customer-002"), loan("different-case", "CUSTOMER-001"),
                loan("legacy", null)));

        assertThat(service.search(query, status, productCode, 20)).extracting(LoanSearchResponse::loanId)
                .containsExactly("own");
        verify(searchRepository).findMatching(Optional.of(OWNER), query, status, productCode, PAGE);
    }

    @Test
    void legacySearchPlaceholderConfersNoOwnership() {
        authenticate("CUSTOMER", "unknown");
        when(searchRepository.findMatching(Optional.of("unknown"), null, null, null, PAGE))
                .thenReturn(List.of(loan("legacy", "unknown")));

        assertThat(service.search(null, null, null, 20)).isEmpty();
    }

    @Test
    void customerCanReadOnlyActivityForTheirExactCustomerIdAndLoanId() {
        authenticate("CUSTOMER", OWNER);
        var own = new LoanActivityDocument("own");
        own.setCustomerId(OWNER);
        var other = new LoanActivityDocument("other");
        other.setCustomerId("CUSTOMER-001");
        when(activityRepository.findByLoanIdAndCustomerId("own", OWNER)).thenReturn(Optional.of(own));
        when(activityRepository.findByLoanIdAndCustomerId("other", OWNER)).thenReturn(Optional.of(other));
        when(activityRepository.findByLoanIdAndCustomerId("legacy", OWNER))
                .thenReturn(Optional.of(new LoanActivityDocument("legacy")));

        assertThat(service.activity("own")).hasValueSatisfying(activity -> assertThat(activity.loanId()).isEqualTo("own"));
        assertThat(service.activity("other")).isEmpty();
        assertThat(service.activity("legacy")).isEmpty();
        assertThat(service.activity("absent")).isEmpty();
        verify(activityRepository).findByLoanIdAndCustomerId("absent", OWNER);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "unknown"})
    void customerCannotReadActivityWithUnusableOwnership(String storedCustomerId) {
        authenticate("CUSTOMER", "unknown");
        var legacy = new LoanActivityDocument("legacy");
        legacy.setCustomerId(storedCustomerId);
        when(activityRepository.findByLoanIdAndCustomerId("legacy", "unknown")).thenReturn(Optional.of(legacy));

        assertThat(service.activity("legacy")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"LOAN_OPERATOR", "LOAN_APPROVER"})
    void loanStaffRetainBroadSearchAndActivity(String role) {
        authenticate(role, OWNER);
        when(searchRepository.findMatching(Optional.empty(), null, null, null, PAGE)).thenReturn(List.of(
                loan("own", OWNER), loan("other", "customer-002"), loan("legacy", null)));
        when(activityRepository.findById("legacy")).thenReturn(Optional.of(new LoanActivityDocument("legacy")));

        assertThat(service.search(null, null, null, 20)).extracting(LoanSearchResponse::loanId)
                .containsExactly("own", "other", "legacy");
        assertThat(service.activity("legacy")).isPresent();

        var placeholder = new LoanActivityDocument("placeholder");
        placeholder.setCustomerId("unknown");
        when(activityRepository.findById("placeholder")).thenReturn(Optional.of(placeholder));
        assertThat(service.activity("placeholder"))
                .hasValueSatisfying(activity -> assertThat(activity.loanId()).isEqualTo("placeholder"));
    }

    @ParameterizedTest
    @CsvSource({
            ", , P-1",
            ", FUNDED, ",
            ", FUNDED, P-1",
            "loan, , ",
            "loan, FUNDED, ",
            "loan, , P-1",
            "loan, FUNDED, P-1"
    })
    void loanStaffSearchPassesEachFilterToTheRepository(String query, String status, String productCode) {
        authenticate("LOAN_OPERATOR", null);
        when(searchRepository.findMatching(Optional.empty(), query, status, productCode, PAGE))
                .thenReturn(List.of(loan("matching", "other-customer")));

        assertThat(service.search(query, status, productCode, 20)).extracting(LoanSearchResponse::loanId)
                .containsExactly("matching");
        verify(searchRepository).findMatching(Optional.empty(), query, status, productCode, PAGE);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"SAVINGS_OPERATOR", "UNKNOWN_ROLE"})
    void otherRolesCannotSearchOrReadActivityEvenWithAnOwnershipClaim(String role) {
        if (role != null) {
            authenticate(role, OWNER);
        }
        assertSearchAndActivityDenied();
    }

    @Test
    void customerWithoutOwnershipClaimCannotSearchOrReadActivity() {
        authenticate("CUSTOMER", null);
        assertSearchAndActivityDenied();
    }

    private void assertSearchAndActivityDenied() {
        assertThatThrownBy(() -> service.search(null, null, null, 20)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.activity("own")).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(searchRepository, activityRepository);
    }

    private static LoanSearchDocument loan(String id, String customerId) {
        return new LoanSearchDocument(id, customerId, "P-1", "FUNDED", BigDecimal.TEN, "standard loan", 1,
                Instant.EPOCH);
    }

    private static void authenticate(String role, String customerId) {
        var token = Jwt.withTokenValue("token").header("alg", "RS256").subject("subject");
        if (customerId != null) {
            token.claim("customer_id", customerId);
        }
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                token.build(), List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}

package com.fincore.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.fincore.platform.web.ApiExceptionHandler;
import com.fincore.platform.web.RequestContextFilter;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class SpringCurrentIdentityTest {

    private final CurrentIdentity identity = new SpringCurrentIdentity();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void customerCanAccessTheirOwnCustomerId() {
        authenticateJwt("customer-001", "ROLE_CUSTOMER");

        assertThat(identity.subject()).isEqualTo("actor-001");
        assertThat(identity.customerId()).contains("customer-001");
        assertThat(identity.hasRole("CUSTOMER")).isTrue();
        assertThat(identity.hasRole("ROLE_CUSTOMER")).isTrue();
        assertThatCode(() -> identity.requireCustomerAccess("customer-001")).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"customer-002", "customer-001 "})
    void rejectsCustomerIdMismatch(String targetCustomerId) {
        authenticateJwt("customer-001", "ROLE_CUSTOMER");

        assertThatThrownBy(() -> identity.requireCustomerAccess(targetCustomerId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectsMissingBlankOrMalformedCustomerId() {
        for (Object claim : new Object[] {null, "  ", 123}) {
            authenticateJwt(claim, "ROLE_CUSTOMER");

            assertThat(identity.customerId()).isEmpty();
            assertThatThrownBy(() -> identity.requireCustomerAccess("123"))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_LOAN_OPERATOR", "ROLE_LOAN_APPROVER", "ROLE_SAVINGS_OPERATOR"})
    void operatorCanAccessAnyCustomerWithoutAnOwnershipClaim(String role) {
        authenticateJwt(null, role);

        assertThat(identity.customerId()).isEmpty();
        assertThatCode(() -> identity.requireCustomerAccess("customer-002")).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"LOAN_OPERATOR", "ROLE_LOAN_OPERATOR"})
    void rejectsPasswordAuthenticationAsACurrentIdentity(String authority) {
        var authentication = UsernamePasswordAuthenticationToken.authenticated("synthetic-actor", "N/A",
                List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThatThrownBy(identity::subject).isInstanceOf(AccessDeniedException.class);
        assertThat(identity.customerId()).isEmpty();
        assertThat(identity.hasRole("LOAN_OPERATOR")).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" \t "})
    void rejectsJwtCustomerWithoutAnAuditableSubject(String subject) {
        Jwt.Builder builder = Jwt.withTokenValue("test-token").header("alg", "RS256")
                .claim("customer_id", "customer-001");
        if (subject != null) {
            builder.subject(subject);
        }
        var authentication = new JwtAuthenticationToken(builder.build(),
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThatThrownBy(() -> identity.requireCustomerAccess("customer-001"))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(identity.hasRole("CUSTOMER")).isFalse();
    }

    @Test
    void rejectsOwnershipClaimFromAnIdentityWithoutAnAllowedRole() {
        authenticateJwt("customer-001", "ROLE_REPORT_VIEWER");

        assertThatThrownBy(() -> identity.requireCustomerAccess("customer-001"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectsAnonymousIdentityEvenWithAnOperatorAuthority() {
        var authentication = new AnonymousAuthenticationToken("test-key", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_SAVINGS_OPERATOR")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThat(identity.hasRole("SAVINGS_OPERATOR")).isFalse();
        assertThatThrownBy(() -> identity.requireCustomerAccess("customer-002"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectsUnsupportedAuthenticatedIdentityEvenWithAnOperatorAuthority() {
        var authentication = new TestingAuthenticationToken("test-actor", "N/A", "ROLE_LOAN_OPERATOR");
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThat(authentication.isAuthenticated()).isTrue();
        assertThatThrownBy(() -> identity.requireCustomerAccess("customer-002"))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(identity.hasRole("LOAN_OPERATOR")).isFalse();
        assertThatThrownBy(identity::subject).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void sharedExceptionHandlerRendersOwnershipDenialAsForbidden() throws Exception {
        authenticateJwt("customer-001", "ROLE_CUSTOMER");
        var mockMvc = MockMvcBuilders.standaloneSetup(new OwnershipController(identity))
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new RequestContextFilter())
                .build();

        mockMvc.perform(get("/ownership"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    private static void authenticateJwt(Object customerId, String role) {
        Jwt.Builder builder = Jwt.withTokenValue("test-token").header("alg", "RS256").subject("actor-001");
        if (customerId != null) {
            builder.claim("customer_id", customerId);
        }
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(builder.build(), List.of(new SimpleGrantedAuthority(role))));
    }

    @RestController
    static class OwnershipController {

        private final CurrentIdentity identity;

        OwnershipController(CurrentIdentity identity) {
            this.identity = identity;
        }

        @GetMapping("/ownership")
        void requireOwnership() {
            identity.requireCustomerAccess("customer-002");
        }
    }
}

class SecurityFailureResponseWriterTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final SecurityFailureResponseWriter writer = new SecurityFailureResponseWriter(objectMapper);

    @ParameterizedTest
    @EnumSource(value = HttpStatus.class, names = {"UNAUTHORIZED", "FORBIDDEN"})
    void writesStandardFailureWithoutTokenOrValidationDetails(HttpStatus status) throws Exception {
        var request = new MockHttpServletRequest("GET", "/protected");
        request.addHeader("Authorization", "Bearer sensitive-test-token");
        request.setAttribute(RequestDispatcher.ERROR_MESSAGE, "signature validation failed");
        request.setAttribute(RequestContextFilter.CORRELATION_ATTRIBUTE, "corr-from-context");
        request.addHeader(RequestContextFilter.CORRELATION_HEADER, "corr-from-header");
        request.addHeader(RequestContextFilter.REQUEST_HEADER, "req-from-header");
        var response = new MockHttpServletResponse();

        writer.write(request, response, status);

        String body = response.getContentAsString();
        var json = objectMapper.readTree(body);
        assertThat(response.getStatus()).isEqualTo(status.value());
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getHeader(RequestContextFilter.CORRELATION_HEADER)).isEqualTo("corr-from-context");
        assertThat(response.getHeader(RequestContextFilter.REQUEST_HEADER)).isEqualTo("req-from-header");
        assertThat(json.at("/success").asBoolean()).isFalse();
        assertThat(json.at("/error/code").asString()).isEqualTo(status.name());
        assertThat(json.at("/error/details").isArray()).isTrue();
        assertThat(json.at("/error/details").size()).isZero();
        assertThat(json.at("/meta/path").asString()).isEqualTo("/protected");
        assertThat(json.at("/meta/correlationId").asString()).isEqualTo("corr-from-context");
        assertThat(json.at("/meta/requestId").asString()).isEqualTo("req-from-header");
        assertThat(body).doesNotContain("sensitive-test-token", "signature validation failed");
    }

    @Test
    void generatesTracingIdsWhenTheRequestContextFilterHasNotRun() throws Exception {
        var request = new MockHttpServletRequest("GET", "/protected");
        var response = new MockHttpServletResponse();

        writer.write(request, response, HttpStatus.UNAUTHORIZED);

        var json = objectMapper.readTree(response.getContentAsString());
        String correlationId = response.getHeader(RequestContextFilter.CORRELATION_HEADER);
        String requestId = response.getHeader(RequestContextFilter.REQUEST_HEADER);
        assertThat(UUID.fromString(correlationId)).isNotEqualTo(UUID.fromString(requestId));
        assertThat(json.at("/meta/correlationId").asString()).isEqualTo(correlationId);
        assertThat(json.at("/meta/requestId").asString()).isEqualTo(requestId);
    }
}

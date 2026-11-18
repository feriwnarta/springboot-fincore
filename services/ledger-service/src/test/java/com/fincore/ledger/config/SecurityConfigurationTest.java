package com.fincore.ledger.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fincore.ledger.api.controller.SavingsController;
import com.fincore.ledger.application.command.OpenSavingsAccountCommand;
import com.fincore.ledger.application.service.SavingsService;
import com.fincore.ledger.domain.enums.SavingsAccountStatus;
import com.fincore.ledger.domain.model.SavingsAccount;
import com.fincore.platform.security.CurrentIdentity;
import com.fincore.platform.web.ApiExceptionHandler;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringJUnitWebConfig(SecurityConfigurationTest.WebConfig.class)
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8180/realms/fincore",
        "fincore.security.audience=fincore-api"
})
@MockitoBean(types = SavingsService.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class SecurityConfigurationTest {

    private static final String ISSUER = "http://localhost:8180/realms/fincore";
    private static final String AUDIENCE = "fincore-api";
    private static final String WRITE_ROLE = "SAVINGS_OPERATOR";
    private static final String WRITE_PATH = "/api/v1/savings/accounts";
    private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static HttpServer jwksServer;
    private static RSAKey signingKey;

    private final WebApplicationContext context;
    private final MockMvc mockMvc;

    SecurityConfigurationTest(WebApplicationContext context) {
        this.context = context;
        this.mockMvc = mockMvc(context);
    }

    private static MockMvc mockMvc(WebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    @DynamicPropertySource
    static void jwksProperties(DynamicPropertyRegistry registry) throws IOException, JOSEException {
        startJwksServer();
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/jwks");
    }

    private static synchronized void startJwksServer() throws IOException, JOSEException {
        if (jwksServer != null) {
            return;
        }
        signingKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        byte[] jwks = new JWKSet(signingKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jwksServer.createContext("/jwks", exchange -> {
            try (exchange) {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jwks.length);
                exchange.getResponseBody().write(jwks);
            }
        });
        jwksServer.start();
    }

    @AfterAll
    static void stopJwksServer() {
        if (jwksServer != null) {
            jwksServer.stop(0);
        }
    }

    @Test
    void missingTokenReturnsTheStandardUnauthorizedEnvelope() throws Exception {
        mockMvc.perform(write().header("X-Correlation-Id", "corr-001").header("X-Request-Id", "req-001"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(header().string("X-Correlation-Id", "corr-001"))
                .andExpect(header().string("X-Request-Id", "req-001"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.meta.correlationId").value("corr-001"))
                .andExpect(jsonPath("$.meta.requestId").value("req-001"));
    }

    @Test
    void malformedTokenIsRejectedWithoutLeakingIt() throws Exception {
        String invalidToken = "invalid-sensitive-token";
        mockMvc.perform(write().header("Authorization", bearer(invalidToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer error=\"invalid_token\""))
                .andExpect(jsonPath("$.error.message").value("Authentication required"))
                .andExpect(jsonPath("$.meta.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty())
                .andExpect(content().string(not(containsString(invalidToken))));
    }

    @Test
    void ignoresSyntheticIdentityHeadersByDefault() throws Exception {
        mockMvc.perform(write().header("X-Actor-Id", "spoofed-id").header("X-Actor-Role", WRITE_ROLE))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {WRITE_ROLE, "CUSTOMER"})
    void verifiedJwtSuppliesAuthorityAndAuditIdentity(String role) throws Exception {
        var service = context.getBean(SavingsService.class);
        when(service.open(any())).thenAnswer(invocation -> {
            OpenSavingsAccountCommand command = invocation.getArgument(0);
            assertThat(command.actorId()).isEqualTo("jwt-subject");
            assertThat(command.actorRole()).isEqualTo(role);
            var identity = context.getBean(CurrentIdentity.class);
            assertThat(identity.subject()).isEqualTo("jwt-subject");
            assertThat(identity.hasRole(role)).isTrue();
            assertThat(identity.customerId()).contains("customer-001");
            assertThat(command.customerId()).isEqualTo("customer-001");
            return savingsAccount();
        });

        mockMvc.perform(write().header("Authorization", bearer(token(ISSUER, AUDIENCE, role)))
                        .header("X-Actor-Id", "spoofed-id").header("X-Actor-Role", "CUSTOMER")
                        .header("X-Correlation-Id", "success-correlation").header("X-Request-Id", "success-request"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accountId").value(ACCOUNT_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.meta.correlationId").value("success-correlation"))
                .andExpect(jsonPath("$.meta.requestId").value("success-request"))
                .andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());
    }

    @Test
    void rejectsTokensWithWrongAudienceOrIssuer() throws Exception {
        mockMvc.perform(write().header("Authorization", bearer(token(ISSUER, "another-api", WRITE_ROLE))))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer error=\"invalid_token\""))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mockMvc.perform(write().header("Authorization",
                        bearer(token("http://localhost:8180/realms/other", AUDIENCE, WRITE_ROLE))))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer error=\"invalid_token\""))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void rejectsExpiredTokensAndSignaturesFromUntrustedKeys() throws Exception {
        Instant now = Instant.now();
        String expired = sign(ISSUER, AUDIENCE, WRITE_ROLE, now.minusSeconds(600), now.minusSeconds(300), signingKey);
        var otherKey = new RSAKeyGenerator(2048).keyID(signingKey.getKeyID()).generate();
        String untrusted = sign(ISSUER, AUDIENCE, WRITE_ROLE, now, now.plusSeconds(300), otherKey);
        for (String jwt : List.of(expired, untrusted)) {
            mockMvc.perform(write().header("Authorization", bearer(jwt)))
                    .andExpect(status().isUnauthorized())
                        .andExpect(header().string("WWW-Authenticate", "Bearer error=\"invalid_token\""))
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        }
    }

    @Test
    void returnsForbiddenForInsufficientRoleEvenWithPrivilegedHeaders() throws Exception {
        mockMvc.perform(post(WRITE_PATH + "/" + ACCOUNT_ID + "/status")
                        .contentType("application/json").content("""
                                {"status":"FROZEN"}
                                """)
                        .header("Authorization", bearer(token(ISSUER, AUDIENCE, "CUSTOMER")))
                        .header("X-Actor-Id", "spoofed-id").header("X-Actor-Role", WRITE_ROLE)
                        .header("X-Correlation-Id", "corr-403").header("X-Request-Id", "req-403"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("WWW-Authenticate", "Bearer error=\"insufficient_scope\""))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.meta.correlationId").value("corr-403"))
                .andExpect(jsonPath("$.meta.requestId").value("req-403"));
        verifyNoInteractions(context.getBean(SavingsService.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"default", "prod", "prod,test"})
    void productionChainRejectsSyntheticModeRegardlessOfProfile(String profile) {
        new WebApplicationContextRunner()
                .withUserConfiguration(WebConfig.class)
                .withBean(SavingsService.class, () -> mock(SavingsService.class))
                .withPropertyValues(
                        "spring.security.oauth2.resourceserver.jwt.issuer-uri=" + ISSUER,
                        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=" + ISSUER + "/certs",
                        "fincore.security.audience=" + AUDIENCE)
                .withInitializer(application -> {
                    var environment = application.getEnvironment();
                    if (!"default".equals(profile)) {
                        environment.setActiveProfiles(profile.split(","));
                    }
                    environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("modeOverride",
                            Map.of("FINCORE_SECURITY_MODE", "synthetic")));
                })
                .run(application -> {
                    assertThat(application).hasFailed();
                    assertThat(application.getStartupFailure())
                            .hasRootCauseMessage("fincore.security.mode must be oauth2");
                });
    }

    @Test
    void allowsAnonymousHealthAndReadiness() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator", "/actuator/info"})
    void protectsOtherActuatorReads(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void appliesTheReadPolicy() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-config")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/journals/by-business-ref/ref-001")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/journals/by-business-ref/ref-001")
                        .header("Authorization", bearer(token(ISSUER, AUDIENCE, "CUSTOMER"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/savings/accounts/11111111-1111-1111-1111-111111111111"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @CsvSource({"GET, ''", "HEAD, ''", "GET, /statement", "HEAD, /statement"})
    void deniesSavingsReadsToOtherRoles(String method, String suffix) throws Exception {
        var service = context.getBean(SavingsService.class);
        when(service.getAccount(ACCOUNT_ID)).thenReturn(savingsAccount());
        when(service.statement(ACCOUNT_ID, null, 51)).thenReturn(List.of());
        for (String role : List.of("LOAN_OPERATOR", "LOAN_APPROVER")) {
            mockMvc.perform(request(HttpMethod.valueOf(method), WRITE_PATH + "/" + ACCOUNT_ID + suffix)
                            .header("Authorization", bearer(token(ISSUER, AUDIENCE, role))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({"GET, ''", "HEAD, ''", "GET, /statement", "HEAD, /statement"})
    void customerAndSavingsOperatorCanReachAccountAndStatementReads(String method, String suffix) throws Exception {
        var service = context.getBean(SavingsService.class);
        when(service.getAccount(ACCOUNT_ID)).thenReturn(savingsAccount());
        when(service.statement(ACCOUNT_ID, null, 51)).thenReturn(List.of());

        for (String role : List.of(WRITE_ROLE, "CUSTOMER")) {
            mockMvc.perform(request(HttpMethod.valueOf(method), WRITE_PATH + "/" + ACCOUNT_ID + suffix)
                            .header("Authorization", bearer(token(ISSUER, AUDIENCE, role))))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void ownershipDenialUsesStandardForbiddenEnvelope() throws Exception {
        var service = context.getBean(SavingsService.class);
        when(service.getAccount(ACCOUNT_ID)).thenThrow(new AccessDeniedException("Private ownership details"));
        mockMvc.perform(get(WRITE_PATH + "/" + ACCOUNT_ID)
                        .header("Authorization", bearer(token(ISSUER, AUDIENCE, "CUSTOMER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value("Access denied"));
        verify(service).getAccount(ACCOUNT_ID);
    }

    private static SavingsAccount savingsAccount() {
        return new SavingsAccount(ACCOUNT_ID, "SAV-001", "customer-001", "IDR", SavingsAccountStatus.ACTIVE,
                BigDecimal.ZERO, 1, Instant.EPOCH, Instant.EPOCH);
    }

    private static MockHttpServletRequestBuilder write() {
        return post(WRITE_PATH).header("Idempotency-Key", "test-open")
                .contentType("application/json").content("""
                        {"customerId":"customer-001"}
                        """);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String token(String issuer, String audience, String role) throws JOSEException {
        Instant now = Instant.now();
        return sign(issuer, audience, role, now, now.plusSeconds(300), signingKey);
    }

    private static String sign(String issuer, String audience, String role, Instant issuedAt, Instant expiresAt,
            RSAKey key) throws JOSEException {
        var claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject("jwt-subject")
                .audience(audience)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .claim("customer_id", "customer-001")
                .claim("resource_access", Map.of(AUDIENCE, Map.of("roles", List.of(role))))
                .build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    @Nested
    @MockitoBean(types = JwtDecoder.class)
    @TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
    class DecoderOutage {

        private final WebApplicationContext outageContext;

        DecoderOutage(WebApplicationContext outageContext) {
            this.outageContext = outageContext;
        }

        @Test
        void decoderOutageReturnsSanitizedUnauthorizedResponse() throws Exception {
            String token = "outage-test-token";
            String decoderMessage = "private-jwks-location";
            when(outageContext.getBean(JwtDecoder.class).decode(token))
                    .thenThrow(new JwtException(decoderMessage + " " + token));

            mockMvc(outageContext).perform(write().header("Authorization", bearer(token))
                            .header("X-Correlation-Id", "outage-corr").header("X-Request-Id", "outage-req"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", "Bearer"))
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.error.message").value("Authentication required"))
                    .andExpect(jsonPath("$.error.details").isEmpty())
                    .andExpect(jsonPath("$.meta.correlationId").value("outage-corr"))
                    .andExpect(jsonPath("$.meta.requestId").value("outage-req"))
                    .andExpect(content().string(not(containsString(token))))
                    .andExpect(content().string(not(containsString(decoderMessage))));
        }
    }

    @Nested
    @ContextConfiguration(classes = SyntheticSecurity.class)
    @TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
    class SyntheticMode {

        private final WebApplicationContext syntheticContext;

        SyntheticMode(WebApplicationContext syntheticContext) {
            this.syntheticContext = syntheticContext;
        }

        @Test
        void authenticatesExplicitSyntheticHeaders() throws Exception {
            var service = syntheticContext.getBean(SavingsService.class);
            when(service.open(any())).thenAnswer(invocation -> {
                OpenSavingsAccountCommand command = invocation.getArgument(0);
                assertThat(command.actorId()).isEqualTo("synthetic-id");
                assertThat(command.actorRole()).isEqualTo(WRITE_ROLE);
                throw new IllegalArgumentException("Test business rule");
            });

            mockMvc(syntheticContext).perform(write()
                            .header("X-Actor-Id", "synthetic-id").header("X-Actor-Role", WRITE_ROLE))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SyntheticSecurity {

        @Bean
        @Order(0)
        SecurityFilterChain syntheticWriteSecurity(HttpSecurity http) throws Exception {
            http.securityMatcher(WRITE_PATH)
                    .csrf(AbstractHttpConfigurer::disable)
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .addFilterBefore((request, response, chain) -> {
                        var httpRequest = (HttpServletRequest) request;
                        String actorId = httpRequest.getHeader("X-Actor-Id");
                        String actorRole = httpRequest.getHeader("X-Actor-Role");
                        if (StringUtils.hasText(actorId) && StringUtils.hasText(actorRole)) {
                            var securityContext = SecurityContextHolder.createEmptyContext();
                            securityContext.setAuthentication(new JwtAuthenticationToken(
                                    Jwt.withTokenValue("synthetic").header("alg", "none").subject(actorId).build(), List.of(new SimpleGrantedAuthority("ROLE_" + actorRole))));
                            SecurityContextHolder.setContext(securityContext);
                        }
                        chain.doFilter(request, response);
                    }, AnonymousAuthenticationFilter.class)
                    .authorizeHttpRequests(auth -> auth.anyRequest().hasRole(WRITE_ROLE));
            return http.build();
        }
    }

    @RestController
    static class ReadEndpoints {

        @GetMapping({"/actuator/health", "/actuator/health/readiness", "/actuator", "/actuator/info",
                "/v3/api-docs", "/swagger-config"})
        void endpoint() {
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfiguration.class, SavingsController.class, ApiExceptionHandler.class, ReadEndpoints.class})
    static class WebConfig {

        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }
    }
}

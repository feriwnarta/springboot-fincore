package com.fincore.search.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fincore.platform.security.CurrentIdentity;
import com.fincore.platform.web.RequestContextFilter;
import com.fincore.search.api.dto.response.LoanActivityResponse;
import com.fincore.search.application.service.LoanSearchQueryService;
import com.fincore.search.infrastructure.search.LoanActivityDocumentRepository;
import com.fincore.search.infrastructure.search.LoanSearchDocumentRepository;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "management.health.elasticsearch.enabled=false",
        "management.health.mongodb.enabled=false"
})
class SecurityConfigurationTest {

    private static final String ISSUER = "http://localhost:8180/realms/fincore";
    private static final String AUDIENCE = "fincore-api";
    private static final String SEARCH = "/api/v1/search/loans";
    private static final String ACTIVITY = "/api/v1/loans/loan-1/activity";
    private static final String OPERATOR = "LOAN_OPERATOR";
    private static final String CORRELATION_ID = "11111111-1111-4111-8111-000000000071";
    private static final String REQUEST_ID = "request-1";
    private static final AtomicBoolean JWKS_UNAVAILABLE = new AtomicBoolean();
    private static final AtomicBoolean JWKS_FAILURE_SERVED = new AtomicBoolean();
    private static RSAKey signingKey;
    private static HttpServer jwksServer;

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private LoanSearchDocumentRepository searchRepository;

    @MockitoBean
    private LoanActivityDocumentRepository activityRepository;

    @MockitoBean
    private LoanSearchQueryService queryService;

    private MockMvc mockMvc;

    @DynamicPropertySource
    static void jwksProperties(DynamicPropertyRegistry registry) throws IOException, JOSEException {
        signingKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        byte[] jwks = new JWKSet(signingKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jwksServer.createContext("/jwks", exchange -> {
            try (exchange) {
                if (JWKS_UNAVAILABLE.get()) {
                    JWKS_FAILURE_SERVED.set(true);
                    exchange.sendResponseHeaders(503, -1);
                    return;
                }
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jwks.length);
                exchange.getResponseBody().write(jwks);
            }
        });
        jwksServer.start();
        registry.add("OIDC_JWK_SET_URI", () -> "http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/jwks");
    }

    @AfterAll
    static void stopJwksServer() {
        if (jwksServer != null) {
            jwksServer.stop(0);
        }
    }

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(RequestContextFilter.class),
                        context.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {SEARCH, ACTIVITY})
    void missingTokenReturnsUnauthorized(String path) throws Exception {
        expectFailure(mockMvc.perform(get(path)
                        .header("X-Correlation-Id", CORRELATION_ID).header("X-Request-Id", REQUEST_ID)),
                401, "UNAUTHORIZED", "Authentication required", "Bearer");
        verifyNoInteractions(queryService);
    }

    @ParameterizedTest
    @CsvSource({
            SEARCH + ", SAVINGS_OPERATOR", ACTIVITY + ", SAVINGS_OPERATOR"
    })
    void savingsOperatorsReceiveForbidden(String path, String role) throws Exception {
        expectFailure(authenticatedGet(path, token(role)), 403, "FORBIDDEN", "Access denied",
                "Bearer error=\"insufficient_scope\"");
        verifyNoInteractions(queryService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LOAN_OPERATOR", "LOAN_APPROVER", "CUSTOMER"})
    void permittedRolesCanSearchAndReadActivity(String role) throws Exception {
        when(queryService.search(null, null, null, 20)).thenReturn(List.of());
        when(queryService.activity("loan-1"))
                .thenReturn(Optional.of(new LoanActivityResponse("loan-1", "FUNDED", List.of())));
        String token = token(role);

        authenticatedGet(SEARCH, token)
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist("JSESSIONID"))
                .andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
        authenticatedGet(ACTIVITY, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.loanId").value("loan-1"));
    }

    @ParameterizedTest
    @CsvSource({
            "http://localhost:8180/realms/other, fincore-api, 300",
            ISSUER + ", other-api, 300",
            ISSUER + ", fincore-api, -300"
    })
    void invalidClaimsReturnSanitizedUnauthorized(String issuer, String audience, long expiryOffset) throws Exception {
        String token = sign(issuer, audience, OPERATOR, expiryOffset, signingKey.getKeyID());

        expectFailure(authenticatedGet(SEARCH, token), 401, "UNAUTHORIZED", "Authentication required",
                "Bearer error=\"invalid_token\"")
                .andExpect(content().string(not(containsString(token))));
        verifyNoInteractions(queryService);
    }

    @Test
    void jwksOutageReturnsSanitizedUnauthorized() throws Exception {
        // A new key ID forces a fetch even if another test has already populated the decoder's cache.
        String token = sign(ISSUER, AUDIENCE, OPERATOR, 300, "uncached-key");
        JWKS_UNAVAILABLE.set(true);
        try {
            expectFailure(authenticatedGet(SEARCH, token), 401, "UNAUTHORIZED", "Authentication required", "Bearer")
                    .andExpect(content().string(not(containsString(token))))
                    .andExpect(content().string(not(containsString("/jwks"))));
            assertThat(JWKS_FAILURE_SERVED).isTrue();
            verifyNoInteractions(queryService);
        } finally {
            JWKS_UNAVAILABLE.set(false);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/actuator/health/readiness", "/v3/api-docs", "/v3/api-docs.yaml",
            "/swagger-ui/index.html"})
    void approvedEndpointsArePublic(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isOk());
    }

    @Test
    void swaggerRedirectIsPublic() throws Exception {
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/info", "/actuator/health/liveness", "/unlisted"})
    void unlistedEndpointsAreNotPublic(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
    }

    @Test
    void exposesSharedCurrentIdentity() {
        assertThat(context.getBeansOfType(CurrentIdentity.class)).hasSize(1);
    }

    private ResultActions authenticatedGet(String path, String token) throws Exception {
        return mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-Correlation-Id", CORRELATION_ID).header("X-Request-Id", REQUEST_ID));
    }

    private static ResultActions expectFailure(ResultActions result, int httpStatus, String code, String message,
            String challenge) throws Exception {
        return result.andExpect(status().is(httpStatus))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, challenge))
                .andExpect(header().string("X-Correlation-Id", CORRELATION_ID))
                .andExpect(header().string("X-Request-Id", REQUEST_ID))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(code))
                .andExpect(jsonPath("$.error.message").value(message))
                .andExpect(jsonPath("$.error.details").isEmpty())
                .andExpect(jsonPath("$.meta.correlationId").value(CORRELATION_ID))
                .andExpect(jsonPath("$.meta.requestId").value(REQUEST_ID));
    }

    private static String token(String role) throws JOSEException {
        return sign(ISSUER, AUDIENCE, role, 300, signingKey.getKeyID());
    }

    private static String sign(String issuer, String audience, String role, long expiryOffset, String keyId)
            throws JOSEException {
        Instant now = Instant.now();
        var claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject("jwt-subject")
                .audience(audience)
                .issueTime(Date.from(now.minusSeconds(600)))
                .expirationTime(Date.from(now.plusSeconds(expiryOffset)))
                .claim("customer_id", "customer-001")
                .claim("resource_access", Map.of(AUDIENCE, Map.of("roles", List.of(role))))
                .build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyId).build(), claims);
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }
}

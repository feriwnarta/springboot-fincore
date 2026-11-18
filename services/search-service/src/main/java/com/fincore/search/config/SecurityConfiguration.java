package com.fincore.search.config;

import com.fincore.platform.security.AudienceValidator;
import com.fincore.platform.security.CurrentIdentity;
import com.fincore.platform.security.KeycloakJwtGrantedAuthoritiesConverter;
import com.fincore.platform.security.SecurityFailureResponseWriter;
import com.fincore.platform.security.SpringCurrentIdentity;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain searchSecurity(HttpSecurity http, ObjectMapper objectMapper, JwtDecoder jwtDecoder)
            throws Exception {
        var failures = new SecurityFailureResponseWriter(objectMapper);
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakJwtGrantedAuthoritiesConverter());
        String[] loanReadRoutes = {"/api/v1/search/loans", "/api/v1/loans/{loanId}/activity"};
        String[] loanReadRoles = {"CUSTOMER", "LOAN_OPERATOR", "LOAN_APPROVER"};
        String[] catalogReadRoutes = {"/api/v1/loan-applications", "/api/v1/savings/accounts"};
        String[] catalogReadRoles = {"CUSTOMER", "SAVINGS_OPERATOR", "LOAN_OPERATOR", "LOAN_APPROVER"};

        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(failures::writeUnauthorized)
                        .accessDeniedHandler(failures::writeForbidden))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/readiness").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/v3/api-docs.yaml", "/swagger-ui/**", "/swagger-ui.html",
                                "/swagger-config")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/loan-products").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/api/v1/loan-products").permitAll()
                        .requestMatchers(HttpMethod.GET, catalogReadRoutes).hasAnyRole(catalogReadRoles)
                        .requestMatchers(HttpMethod.HEAD, catalogReadRoutes).hasAnyRole(catalogReadRoles)
                        .requestMatchers(HttpMethod.GET, loanReadRoutes).hasAnyRole(loanReadRoles)
                        .requestMatchers(HttpMethod.HEAD, loanReadRoutes).hasAnyRole(loanReadRoles)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(failures::writeUnauthorized)
                        .accessDeniedHandler(failures::writeForbidden)
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(converter))
                        .withObjectPostProcessor(new ObjectPostProcessor<BearerTokenAuthenticationFilter>() {
                            @Override
                            public <T extends BearerTokenAuthenticationFilter> T postProcess(T filter) {
                                // Decoder service failures, including a JWKS outage, use the same sanitized response.
                                filter.setAuthenticationFailureHandler(failures::writeUnauthorized);
                                return filter;
                            }
                        }));
        return http.build();
    }

    @Bean
    CurrentIdentity currentIdentity() {
        return new SpringCurrentIdentity();
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${fincore.security.audience}") String audience) {
        var decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        var timestamps = new JwtTimestampValidator();
        timestamps.setAllowEmptyExpiryClaim(false);
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
                timestamps, new JwtIssuerValidator(issuer), new AudienceValidator(audience),
                new JwtClaimValidator<>(JwtClaimNames.SUB, subject -> subject instanceof String value
                        && StringUtils.hasText(value))));
        return decoder;
    }
}

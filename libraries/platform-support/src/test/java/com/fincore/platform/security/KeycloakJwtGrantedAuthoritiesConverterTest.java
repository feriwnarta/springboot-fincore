package com.fincore.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakJwtGrantedAuthoritiesConverterTest {

    private final KeycloakJwtGrantedAuthoritiesConverter converter = new KeycloakJwtGrantedAuthoritiesConverter();

    @Test
    void mapsFincoreClientRoles() {
        Jwt jwt = jwt(Map.of("fincore-api", Map.of("roles", List.of("CUSTOMER", "SAVINGS_OPERATOR"))));

        assertThat(converter.convert(jwt)).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_CUSTOMER", "ROLE_SAVINGS_OPERATOR");
    }

    @Test
    void ignoresRolesFromOtherClientsAndTheRealm() {
        Jwt jwt = jwt(Map.of("other-client", Map.of("roles", List.of("SAVINGS_OPERATOR"))));

        assertThat(converter.convert(jwt)).isEmpty();
    }

    @Test
    void ignoresMalformedRoleClaimsAndEntries() {
        assertThat(converter.convert(jwt("SAVINGS_OPERATOR"))).isEmpty();
        assertThat(converter.convert(jwt(Map.of("fincore-api", "SAVINGS_OPERATOR")))).isEmpty();
        assertThat(converter.convert(jwt(Map.of("fincore-api", Map.of("roles", "SAVINGS_OPERATOR"))))).isEmpty();
        Jwt jwt = jwt(Map.of("fincore-api", Map.of("roles", List.of("CUSTOMER", 1, " "))));

        assertThat(converter.convert(jwt)).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_CUSTOMER");
    }

    private static Jwt jwt(Object resourceAccess) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject("actor-001")
                .claim("resource_access", resourceAccess)
                .claim("realm_access", Map.of("roles", List.of("LOAN_OPERATOR")))
                .build();
    }
}

class AudienceValidatorTest {

    private final AudienceValidator validator = new AudienceValidator("fincore-api");

    @Test
    void acceptsTheConfiguredAudienceInEitherJwtAudienceForm() {
        assertThat(validator.validate(jwt(List.of("other-client", "fincore-api"))).hasErrors()).isFalse();
        assertThat(validator.validate(jwt("fincore-api")).hasErrors()).isFalse();
    }

    @Test
    void rejectsMissingWrongOrMalformedAudiences() {
        assertThat(validator.validate(jwt(null)).hasErrors()).isTrue();
        assertThat(validator.validate(jwt(List.of("fincore-api-other"))).hasErrors()).isTrue();
        assertThat(validator.validate(jwt(List.of("fincore-api", 1))).hasErrors()).isTrue();
    }

    private static Jwt jwt(Object audience) {
        Jwt.Builder builder = Jwt.withTokenValue("test-token").header("alg", "RS256").subject("actor-001");
        if (audience != null) {
            builder.claim("aud", audience);
        }
        return builder.build();
    }
}

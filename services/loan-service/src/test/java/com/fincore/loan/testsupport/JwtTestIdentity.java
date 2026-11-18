package com.fincore.loan.testsupport;

import java.util.Arrays;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class JwtTestIdentity {

    private JwtTestIdentity() {
    }

    public static void authenticate(String subject, String customerId, String... roles) {
        var jwt = Jwt.withTokenValue("test-token").header("alg", "RS256").subject(subject);
        if (customerId != null) {
            jwt.claim("customer_id", customerId);
        }
        var authorities = Arrays.stream(roles)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        var authentication = new JwtAuthenticationToken(jwt.build(), authorities);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}

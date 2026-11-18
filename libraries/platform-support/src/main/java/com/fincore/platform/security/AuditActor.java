package com.fincore.platform.security;

import java.util.Arrays;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;

public record AuditActor(String id, String role) {

    public static AuditActor from(CurrentIdentity identity, String... permittedRoles) {
        String role = Arrays.stream(permittedRoles)
                .filter(identity::hasRole)
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Access denied"));
        return new AuditActor(identity.subject(), role);
    }

    public static AuditActor from(Authentication authentication, String... permittedRoles) {
        if (authentication == null || !authentication.isAuthenticated() || !StringUtils.hasText(authentication.getName())) {
            throw new AccessDeniedException("Access denied");
        }

        String role = Arrays.stream(permittedRoles)
                .filter(candidate -> authentication.getAuthorities().stream()
                        .anyMatch(authority -> ("ROLE_" + candidate).equals(authority.getAuthority())))
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Access denied"));
        return new AuditActor(authentication.getName(), role);
    }
}

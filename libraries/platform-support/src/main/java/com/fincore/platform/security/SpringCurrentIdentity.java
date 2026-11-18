package com.fincore.platform.security;

import java.util.Optional;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;

public final class SpringCurrentIdentity implements CurrentIdentity {

    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public String subject() {
        JwtAuthenticationToken authentication = currentAuthentication();
        String subject = authentication == null ? null : authentication.getName();
        if (!StringUtils.hasText(subject)) {
            throw new AccessDeniedException("Access denied");
        }
        return subject;
    }

    @Override
    public Optional<String> customerId() {
        return customerId(currentAuthentication());
    }

    @Override
    public boolean hasRole(String role) {
        if (!StringUtils.hasText(role)) {
            return false;
        }
        String roleName = role.startsWith(ROLE_PREFIX) ? role.substring(ROLE_PREFIX.length()) : role;
        return hasRole(currentAuthentication(), roleName);
    }

    @Override
    public void requireCustomerAccess(String customerId) {
        requireCustomerAccess(customerId, "LOAN_OPERATOR", "LOAN_APPROVER", "SAVINGS_OPERATOR");
    }

    private static JwtAuthenticationToken currentAuthentication() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken authentication
                && authentication.isAuthenticated() && StringUtils.hasText(authentication.getName())) {
            return authentication;
        }
        return null;
    }

    private static Optional<String> customerId(JwtAuthenticationToken authentication) {
        Object claim = authentication == null ? null : authentication.getToken().getClaim("customer_id");
        return claim instanceof String id && StringUtils.hasText(id) ? Optional.of(id) : Optional.empty();
    }

    private static boolean hasRole(JwtAuthenticationToken authentication, String role) {
        if (authentication == null) {
            return false;
        }
        String prefixedRole = ROLE_PREFIX + role;
        return authentication.getAuthorities().stream().anyMatch(authority -> prefixedRole.equals(authority.getAuthority()));
    }
}

package com.fincore.platform.security;

import java.util.Arrays;
import java.util.Optional;

import org.springframework.security.access.AccessDeniedException;

public interface CurrentIdentity {

    String subject();

    Optional<String> customerId();

    boolean hasRole(String role);

    void requireCustomerAccess(String customerId);

    default void requireCustomerAccess(String customerId, String... operatorRoles) {
        if (Arrays.stream(operatorRoles).anyMatch(this::hasRole)) {
            return;
        }
        if (hasRole("CUSTOMER") && customerId().filter(id -> id.equals(customerId)).isPresent()) {
            return;
        }
        throw new AccessDeniedException("Customer access denied");
    }
}

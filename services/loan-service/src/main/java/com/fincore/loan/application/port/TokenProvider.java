package com.fincore.loan.application.port;

public interface TokenProvider {

    TokenResult passwordGrant(String username, String password);

    record TokenResult(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            long refreshExpiresIn,
            String scope) {
    }
}

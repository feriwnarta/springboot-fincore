package com.fincore.loan.application.service;

import com.fincore.loan.api.dto.request.LoginRequest;
import com.fincore.loan.api.dto.response.LoginResponse;
import com.fincore.loan.application.port.TokenProvider;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final TokenProvider tokenProvider;

    public LoginResponse login(LoginRequest request) {
        TokenProvider.TokenResult token = tokenProvider.passwordGrant(request.username(), request.password());
        return new LoginResponse(token.accessToken(), token.refreshToken(), token.tokenType(),
                token.expiresIn(), token.refreshExpiresIn(), token.scope());
    }
}

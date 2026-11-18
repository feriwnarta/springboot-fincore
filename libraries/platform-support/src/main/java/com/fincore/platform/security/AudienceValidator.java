package com.fincore.platform.security;

import java.util.Collection;

import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.util.Assert;

public final class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private final JwtClaimValidator<Object> validator;

    public AudienceValidator(String audience) {
        Assert.hasText(audience, "Audience must not be blank");
        this.validator = new JwtClaimValidator<>(JwtClaimNames.AUD, claim -> audience.equals(claim)
                || (claim instanceof Collection<?> audiences
                        && audiences.stream().allMatch(String.class::isInstance)
                        && audiences.contains(audience)));
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        return validator.validate(token);
    }
}

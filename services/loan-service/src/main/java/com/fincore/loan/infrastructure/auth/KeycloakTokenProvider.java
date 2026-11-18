package com.fincore.loan.infrastructure.auth;

import java.util.Map;

import com.fincore.loan.application.port.TokenProvider;
import com.fincore.platform.web.AuthenticationFailedException;
import com.fincore.platform.web.UpstreamServiceException;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@RequiredArgsConstructor
public class KeycloakTokenProvider implements TokenProvider {

    private static final ParameterizedTypeReference<Map<String, Object>> TOKEN_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;

    @Value("${fincore.auth.keycloak-token-url}")
    private String tokenUrl;

    @Value("${fincore.auth.client-id:fincore-cli}")
    private String clientId;

    @Override
    public TokenResult passwordGrant(String username, String password) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", clientId);
        form.add("username", username);
        form.add("password", password);

        try {
            Map<String, Object> body = restClient.post()
                    .uri(tokenUrl)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TOKEN_RESPONSE_TYPE);
            return toTokenResult(body);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 400 || exception.getStatusCode().value() == 401) {
                throw new AuthenticationFailedException("Keycloak rejected the credentials");
            }
            throw new UpstreamServiceException("Keycloak returned an unexpected status", exception);
        } catch (RestClientException exception) {
            throw new UpstreamServiceException("Could not reach Keycloak", exception);
        }
    }

    private static TokenResult toTokenResult(Map<String, Object> body) {
        if (body == null) {
            throw new UpstreamServiceException("Keycloak returned an empty token response");
        }
        String accessToken = text(body, "access_token");
        if (accessToken == null || accessToken.isBlank()) {
            throw new UpstreamServiceException("Keycloak returned an invalid token response");
        }
        return new TokenResult(
                accessToken,
                optionalText(body, "refresh_token"),
                optionalText(body, "token_type", "Bearer"),
                number(body, "expires_in"),
                number(body, "refresh_expires_in"),
                optionalText(body, "scope"));
    }

    private static String text(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value instanceof String text ? text : null;
    }

    private static String optionalText(Map<String, Object> body, String key) {
        return optionalText(body, key, null);
    }

    private static String optionalText(Map<String, Object> body, String key, String defaultValue) {
        String value = text(body, key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static long number(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }
}

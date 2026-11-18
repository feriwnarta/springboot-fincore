package com.fincore.platform.security;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import com.fincore.platform.api.ApiError;
import com.fincore.platform.api.ApiMetaFactory;
import com.fincore.platform.api.ApiResponse;
import com.fincore.platform.web.RequestContextFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.BearerTokenErrorCodes;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

public final class SecurityFailureResponseWriter {

    private final ObjectMapper objectMapper;

    public SecurityFailureResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void writeUnauthorized(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException failure) throws IOException {
        boolean invalidToken = failure instanceof OAuth2AuthenticationException oauth2
                && BearerTokenErrorCodes.INVALID_TOKEN.equals(oauth2.getError().getErrorCode());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, invalidToken ? "Bearer error=\"invalid_token\"" : "Bearer");
        write(request, response, HttpStatus.UNAUTHORIZED);
    }

    public void writeForbidden(HttpServletRequest request, HttpServletResponse response, AccessDeniedException failure)
            throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"insufficient_scope\"");
        write(request, response, HttpStatus.FORBIDDEN);
    }

    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status) throws IOException {
        String message = switch (status) {
            case UNAUTHORIZED -> "Authentication required";
            case FORBIDDEN -> "Access denied";
            default -> throw new IllegalArgumentException("Expected UNAUTHORIZED or FORBIDDEN");
        };

        ensureId(request, response, RequestContextFilter.CORRELATION_ATTRIBUTE, RequestContextFilter.CORRELATION_HEADER);
        ensureId(request, response, RequestContextFilter.REQUEST_ATTRIBUTE, RequestContextFilter.REQUEST_HEADER);
        var error = new ApiError(status.name(), message, List.of());
        var body = ApiResponse.failure(error, ApiMetaFactory.from(request));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private static void ensureId(HttpServletRequest request, HttpServletResponse response, String attribute, String header) {
        String id = request.getAttribute(attribute) instanceof String value ? value : null;
        if (!StringUtils.hasText(id)) {
            id = request.getHeader(header);
        }
        if (!StringUtils.hasText(id)) {
            id = response.getHeader(header);
        }
        if (!StringUtils.hasText(id)) {
            id = UUID.randomUUID().toString();
        }
        request.setAttribute(attribute, id);
        response.setHeader(header, id);
    }
}

package com.fincore.platform.api;

import java.time.Instant;

import jakarta.servlet.http.HttpServletRequest;

import com.fincore.platform.web.RequestContextFilter;

public final class ApiMetaFactory {

    private ApiMetaFactory() {
    }

    public static ApiMeta from(HttpServletRequest request) {
        String correlationId = value(request, RequestContextFilter.CORRELATION_ATTRIBUTE,
                RequestContextFilter.CORRELATION_HEADER);
        String requestId = value(request, RequestContextFilter.REQUEST_ATTRIBUTE,
                RequestContextFilter.REQUEST_HEADER);
        return new ApiMeta("v1", correlationId, requestId, Instant.now(), request.getRequestURI());
    }

    private static String value(HttpServletRequest request, String attribute, String header) {
        Object attributeValue = request.getAttribute(attribute);
        if (attributeValue instanceof String value && !value.isBlank()) {
            return value;
        }
        String headerValue = request.getHeader(header);
        return headerValue == null || headerValue.isBlank() ? "unknown" : headerValue;
    }
}

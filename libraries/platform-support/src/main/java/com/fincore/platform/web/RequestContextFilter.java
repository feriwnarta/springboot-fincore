package com.fincore.platform.web;

import java.io.IOException;
import java.util.UUID;

import com.fincore.platform.event.CorrelationIds;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestContextFilter extends OncePerRequestFilter {

    public static final String CORRELATION_HEADER = "X-Correlation-Id";
    public static final String REQUEST_HEADER = "X-Request-Id";
    public static final String CORRELATION_ATTRIBUTE = RequestContextFilter.class.getName() + ".correlationId";
    public static final String REQUEST_ATTRIBUTE = RequestContextFilter.class.getName() + ".requestId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String correlationId = CorrelationIds.resolve(request.getHeader(CORRELATION_HEADER)).toString();
        String requestId = valueOrGenerated(request.getHeader(REQUEST_HEADER));

        request.setAttribute(CORRELATION_ATTRIBUTE, correlationId);
        request.setAttribute(REQUEST_ATTRIBUTE, requestId);
        response.setHeader(CORRELATION_HEADER, correlationId);
        response.setHeader(REQUEST_HEADER, requestId);
        MDC.put("correlationId", correlationId);
        MDC.put("requestId", requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("correlationId");
            MDC.remove("requestId");
        }
    }

    private static String valueOrGenerated(String value) {
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }
}

package com.fincore.platform.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import com.fincore.platform.api.ApiMetaFactory;
import com.fincore.platform.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PlatformWebContractTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ContractController())
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new RequestContextFilter(), new RequestLoggingFilter())
                .build();
    }

    @Test
    void preservesCorrelationIdAndReturnsStandardSuccessEnvelope() throws Exception {
        mockMvc.perform(get("/contract")
                        .header(RequestContextFilter.CORRELATION_HEADER, "11111111-1111-4111-8111-111111111111")
                        .header(RequestContextFilter.REQUEST_HEADER, "req-001"))
                .andExpect(status().isOk())
                .andExpect(contentTypeJson())
                .andExpect(header().string(RequestContextFilter.CORRELATION_HEADER, "11111111-1111-4111-8111-111111111111"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.service").value("platform-support"))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.meta.correlationId").value("11111111-1111-4111-8111-111111111111"))
                .andExpect(jsonPath("$.meta.requestId").value("req-001"))
                .andExpect(jsonPath("$.meta.apiVersion").value("v1"));
    }

    @Test
    void generatesCorrelationAndRequestIdsWhenHeadersAreMissing() throws Exception {
        mockMvc.perform(get("/contract"))
                .andExpect(status().isOk())
                .andExpect(header().exists(RequestContextFilter.CORRELATION_HEADER))
                .andExpect(header().exists(RequestContextFilter.REQUEST_HEADER))
                .andExpect(jsonPath("$.meta.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"customer-correlation", "1-1-1-1-1", "invalid\"\\header"})
    void replacesInvalidCorrelationIdsBeforeTheController(String supplied) throws Exception {
        mockMvc.perform(get("/contract").header(RequestContextFilter.CORRELATION_HEADER, supplied))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String resolved = result.getResponse().getHeader(RequestContextFilter.CORRELATION_HEADER);
                    org.assertj.core.api.Assertions.assertThat(UUID.fromString(resolved).toString())
                            .isEqualTo(resolved).isNotEqualTo(supplied);
                    jsonPath("$.meta.correlationId").value(resolved).match(result);
                });
    }

    @Test
    void mapsBusinessExceptionToStandardErrorEnvelope() throws Exception {
        mockMvc.perform(get("/contract/failure"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(contentTypeJson())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.error.message").value("business rule failed"))
                .andExpect(jsonPath("$.meta.correlationId").isNotEmpty());
    }

    private static ResultMatcher contentTypeJson() {
        return result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentType())
                .startsWith(MediaType.APPLICATION_JSON_VALUE);
    }

    @RestController
    static class ContractController {

        @GetMapping("/contract")
        ApiResponse<Map<String, String>> contract(HttpServletRequest request) {
            return ApiResponse.success(
                    Map.of("service", "platform-support"),
                    ApiMetaFactory.from(request));
        }

        @GetMapping("/contract/failure")
        ApiResponse<Void> failure() {
            throw new IllegalArgumentException("business rule failed");
        }
    }
}

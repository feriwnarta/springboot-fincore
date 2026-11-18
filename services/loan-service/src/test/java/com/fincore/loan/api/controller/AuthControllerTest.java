package com.fincore.loan.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fincore.loan.api.dto.response.LoginResponse;
import com.fincore.loan.application.service.AuthService;
import com.fincore.platform.web.ApiExceptionHandler;
import com.fincore.platform.web.RequestContextFilter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {

    private AuthService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(AuthService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new RequestContextFilter())
                .build();
    }

    @Test
    void logsInUsingTheCommonResponseEnvelope() throws Exception {
        when(service.login(any())).thenReturn(new LoginResponse(
                "access-token", "refresh-token", "Bearer", 300, 1800, "openid profile"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Correlation-Id", "11111111-1111-4111-8111-000000000051")
                        .header("X-Request-Id", "req-auth-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"customer1\",\"password\":\"customer1\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "11111111-1111-4111-8111-000000000051"))
                .andExpect(header().string("X-Request-Id", "req-auth-001"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void rejectsBlankCredentialsWithTheCommonValidationEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}

package com.fincore.ledger.config;

import java.util.List;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        in = SecuritySchemeIn.HEADER,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Keycloak access token")
public class OpenApiConfiguration {

    @Bean
    OpenAPI openAPI(@Value("${FINCORE_API_PUBLIC_BASE_URL:http://localhost:8082}") String publicBaseUrl) {
        return new OpenAPI().servers(List.of(new Server()
                .url(publicBaseUrl)
                .description("Public API")));
    }
}

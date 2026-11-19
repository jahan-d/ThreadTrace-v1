package com.threadtrace.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI threadTraceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ThreadTrace API")
                        .description(
                                "REST API for garment supply-chain traceability and digital product passports."
                        )
                        .version("1.0.0"));
    }
}

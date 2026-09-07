package com.example.outbox.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI outboxOpenApi() {
        Info info = new Info()
                .title("Outbox Pattern API")
                .version("1.0.0")
                .description("Order intake that writes the order and its OrderCreated outbox event in one transaction");
        return new OpenAPI().info(info);
    }
}

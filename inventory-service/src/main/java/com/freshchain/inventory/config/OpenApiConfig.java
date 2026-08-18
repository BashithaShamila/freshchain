package com.freshchain.inventory.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI inventoryOpenApi() {
        final String scheme = "bearer-jwt";
        return new OpenAPI()
                .info(new Info()
                        .title("FreshChain Inventory Service")
                        .version("v1")
                        .description("""
                                Lot-based inventory with first-expired-first-out allocation.

                                Stock is tracked per lot with an expiry date, so every allocation
                                decision names the lots it drew from. Reservations are held, then
                                confirmed and consumed, or released on cancellation or timeout."""))
                .addSecurityItem(new SecurityRequirement().addList(scheme))
                .components(new Components().addSecuritySchemes(scheme,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}

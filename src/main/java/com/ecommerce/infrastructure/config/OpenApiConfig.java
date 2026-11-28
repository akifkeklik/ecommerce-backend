package com.ecommerce.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger configuration for API documentation.
 */
@Configuration
public class OpenApiConfig {

    private static final String REPOSITORY_URL = "https://github.com/akifkeklik/ecommerce-backend";

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("E-Commerce API")
                        .version("1.0.0")
                        .description("Orders with row-locked stock, discount codes, payments with refunds and "
                                + "moderated reviews. Register, log in, then use Authorize with the access token.")
                        .contact(new Contact()
                                .name("Source code and documentation")
                                .url(REPOSITORY_URL))
                        .license(new License()
                                .name("MIT License")
                                .url(REPOSITORY_URL + "/blob/main/LICENSE")))
                // A relative server makes "Try it out" call the host that serves this page,
                // whether that is localhost or the hosted demo.
                .servers(List.of(new Server().url("/").description("This server")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT Authorization header using the Bearer scheme. " +
                                                "Enter your token in the text input below.")));
    }
}

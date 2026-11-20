package com.fintrack.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The machine-readable description of the API (served at /v3/api-docs) and its Swagger UI. */
@Configuration
public class OpenApiConfig {

    static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    OpenAPI fintrackOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("FinTrack API")
                        .version("v1")
                        .description("""
                                REST API of FinTrack, a personal finance and transaction management application.
                                It uses simulated data only; it is not connected to any bank.

                                **Authentication.** Register, log in at `/api/v1/auth/login`, then send the returned
                                token as `Authorization: Bearer <token>`. In Swagger UI use the Authorize button.

                                **Errors** use RFC 9457 problem details (`application/problem+json`) with `title`,
                                `status` and `detail`. Validation errors (400) add an `errors` list of
                                `{field, message}`. Common statuses: 401 not authenticated, 404 not found (also used
                                for another user's data, so existence is not revealed), 409 conflict with current
                                state (duplicate name, archived account, ...), 422 a business rule was broken
                                (for example insufficient funds).

                                **Money** is a decimal number with at most two decimal places. Amounts in
                                different currencies are never added together.
                                """)
                        .contact(new Contact().name("FinTrack").url("https://github.com/")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                // Applies to every operation; the authentication endpoints opt out with @SecurityRequirements.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}

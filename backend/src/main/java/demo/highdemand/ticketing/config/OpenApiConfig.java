package demo.highdemand.ticketing.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;

@OpenAPIDefinition(
        info = @Info(
                title = "High Demand Ticketing API",
                version = "v1",
                description = """
                        REST API for managing entertainment tickets.
                        Supports ticket creation, retrieval, searching,
                        purchasing, cancellation, pagination, filtering,
                        sorting, and JWT-based authorization.
                        """
        ),
        tags = {
                @Tag(
                        name = "Tickets",
                        description = "Operations for managing entertainment tickets"
                )
        }
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        bearerFormat = "JWT",
        scheme = "bearer"
)
public class OpenApiConfig {
}
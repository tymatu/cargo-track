package com.cargotrack.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "CargoTrack API",
                version = "v1",
                description = "Parcel lifecycle, shipment dispatch, live fleet tracking and administration.",
                contact = @Contact(name = "CargoTrack"),
                license = @License(name = "MIT")),
        servers = @Server(url = "/", description = "Current server"))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Access token returned by /api/v1/auth/login")
public class OpenApiConfig {
}

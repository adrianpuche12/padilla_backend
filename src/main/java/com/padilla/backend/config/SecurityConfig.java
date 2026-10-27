package com.padilla.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter;
    private final List<String> allowedOrigins;

    public SecurityConfig(KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter,
                          @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.keycloakJwtAuthenticationConverter = keycloakJwtAuthenticationConverter;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                // Preflight CORS requests
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Endpoints publicos (sin autenticacion)
                // PRO-007: publico solo health/info del actuator (sin detalles) y el login
                .requestMatchers("/health", "/actuator/health", "/actuator/health/**", "/actuator/info", "/api/auth/**").permitAll()
                .requestMatchers("/actuator/**").denyAll()
                // Endpoints solo para roles administrativos
                .requestMatchers("/api/admin/**").hasAnyRole("SUPER_ADMIN", "MANAGER", "ADMIN")
                // Endpoints legacy de usuario
                .requestMatchers("/api/user/**").hasAnyRole("SUPER_ADMIN", "MANAGER", "ADMIN", "OWNER", "TENANT", "PROVIDER")
                // Módulo Clientes — SUPER_ADMIN, MANAGER y ADMIN pueden gestionar clientes
                .requestMatchers("/api/clients/**").hasAnyRole("SUPER_ADMIN", "MANAGER", "ADMIN")
                // Endpoints del dashboard — solo roles internos
                .requestMatchers("/api/leads/**").hasAnyRole("SUPER_ADMIN", "MANAGER", "ADMIN")
                .requestMatchers("/api/sellers/**").hasAnyRole("SUPER_ADMIN", "MANAGER", "ADMIN")
                .requestMatchers("/api/sources/**").hasAnyRole("SUPER_ADMIN", "MANAGER", "ADMIN")
                .requestMatchers("/api/tickets/**").authenticated()
                // Todo lo demas requiere autenticacion
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtAuthenticationConverter))
            );

        return http.build();
    }

    /**
     * PRO-007: CORS solo para los origenes del ambiente (app.cors.allowed-origins, separados por coma).
     * Antes aceptaba cualquier origen con credenciales. En dev/staging/prod el frontend llama por el
     * proxy de nginx (mismo origen), asi que CORS solo hace falta para desarrollo local.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(allowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

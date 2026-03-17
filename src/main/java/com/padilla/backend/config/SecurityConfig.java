package com.padilla.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
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

    public SecurityConfig(KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter) {
        this.keycloakJwtAuthenticationConverter = keycloakJwtAuthenticationConverter;
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
                .requestMatchers("/health", "/actuator/**", "/test", "/api/auth/**").permitAll()
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

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.ignoring()
                .requestMatchers("/api/admin/ping");
    }
}

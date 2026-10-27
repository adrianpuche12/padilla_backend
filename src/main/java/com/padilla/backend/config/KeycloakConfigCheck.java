package com.padilla.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * PRO-010: avisa al arrancar si falta la configuracion de admin de Keycloak. Sin ella el alta de
 * usuarios, el reset y el forgot-password fallan (este ultimo en silencio, porque siempre responde 200).
 */
@Slf4j
@Component
public class KeycloakConfigCheck implements ApplicationRunner {

    private final String adminPassword;
    private final String serverUrl;

    public KeycloakConfigCheck(@Value("${keycloak.admin.password:}") String adminPassword,
                               @Value("${keycloak.admin.server-url:}") String serverUrl) {
        this.adminPassword = adminPassword;
        this.serverUrl = serverUrl;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("KEYCLOAK_ADMIN_PASSWORD no esta configurada: alta de usuarios, reset y forgot-password no van a funcionar");
        }
        if (serverUrl == null || serverUrl.isBlank()) {
            log.warn("KEYCLOAK_URL no esta configurada");
        }
    }

    boolean isAdminConfigured() {
        return adminPassword != null && !adminPassword.isBlank();
    }
}

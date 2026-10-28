package com.padilla.backend.service.settings;

import com.padilla.backend.entity.Agency;
import com.padilla.backend.entity.AgencySettings;
import com.padilla.backend.enums.Plan;
import com.padilla.backend.enums.Role;
import com.padilla.backend.exception.ConflictException;
import com.padilla.backend.repository.AgencyRepository;
import com.padilla.backend.repository.AgencySettingsRepository;
import com.padilla.backend.security.AgencyContext;
import com.padilla.backend.security.RlsBypass;
import com.padilla.backend.service.auth.KeycloakAdminService;
import com.padilla.backend.service.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

/**
 * PRO-023: alta automatica de una inmobiliaria nueva (sin pasos manuales):
 * agencia + configuracion por defecto + SUPER_ADMIN inicial en Keycloak con acceso temporal y mail.
 * Lo ejecuta el personal de plataforma de Nilo (rol PLATFORM_ADMIN de Keycloak).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgencyOnboardingService {

    private final AgencyRepository agencyRepository;
    private final AgencySettingsRepository settingsRepository;
    private final KeycloakAdminService keycloakAdminService;
    private final EmailService emailService;
    private final RlsBypass rlsBypass;
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactionManager;

    @Value("${app.db.schema}")
    private String schema;

    public record Onboarding(Agency agency, UUID superAdminId, String temporaryPassword) {}

    public Onboarding onboard(String name, String slug, Plan plan, String adminName, String adminEmail) {
        String normalizedSlug = slug.toLowerCase().trim();
        String email = adminEmail.toLowerCase().trim();

        Agency agency = rlsBypass.call(() -> {
            if (agencyRepository.existsBySlug(normalizedSlug)) {
                throw new ConflictException("Ya existe una inmobiliaria con el identificador " + normalizedSlug);
            }
            Integer emailTomado = jdbc.queryForObject(
                    "SELECT count(*) FROM " + schema + ".users WHERE lower(email) = ?", Integer.class, email);
            if (emailTomado != null && emailTomado > 0) {
                throw new ConflictException("El email " + email + " ya tiene una cuenta en Propero");
            }
            Agency a = new Agency();
            a.setName(name.trim());
            a.setSlug(normalizedSlug);
            a.setPlan(plan == null ? Plan.STARTER : plan);
            Agency saved = agencyRepository.saveAndFlush(a);
            AgencySettings settings = new AgencySettings();
            settings.setAgencyId(saved.getId());
            settings.setNombreComercial(saved.getName());
            settingsRepository.save(settings);
            return saved;
        });

        // Keycloak primero (su id es el users.id); si falla el INSERT se borra el usuario de Keycloak.
        String keycloakId = keycloakAdminService.createUser(adminName, email, Role.SUPER_ADMIN);
        UUID adminId = UUID.fromString(keycloakId);
        try {
            AgencyContext.callAs(agency.getId(), () -> new TransactionTemplate(transactionManager).execute(s ->
                    jdbc.update("INSERT INTO " + schema + ".users (id, name, email, role, active, created_at, first_login, "
                                    + "password_reset_expires_at) VALUES (?, ?, ?, 'SUPER_ADMIN', TRUE, NOW(), TRUE, NOW() + INTERVAL '24 hours')",
                            adminId, adminName.trim(), email)));
        } catch (RuntimeException e) {
            keycloakAdminService.deleteUserById(keycloakId);
            throw e;
        }
        String tempPassword = keycloakAdminService.generateAndSetTemporaryPassword(keycloakId);
        emailService.sendWelcomeEmail(email, adminName, tempPassword);
        log.info("Inmobiliaria {} ({}) dada de alta con SUPER_ADMIN {}", agency.getName(), agency.getId(), email);
        return new Onboarding(agency, adminId, tempPassword);
    }

    public List<Agency> list() {
        return rlsBypass.call(agencyRepository::findAll);
    }
}

package com.padilla.backend.controller;

import com.padilla.backend.dto.settings.AgencySettingsDTO;
import com.padilla.backend.entity.Agency;
import com.padilla.backend.enums.Plan;
import com.padilla.backend.service.settings.AgencyOnboardingService;
import com.padilla.backend.service.settings.AgencySettingsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PRO-023 / PRO-024 / PRO-025: configuracion de la inmobiliaria y alta de inmobiliarias.
 */
@RestController
@RequiredArgsConstructor
public class SettingsController {

    private final AgencySettingsService settingsService;
    private final AgencyOnboardingService onboardingService;

    /** Configuracion de la inmobiliaria del usuario. La leen todos los internos (documentos, mails). */
    @GetMapping("/api/settings/agency")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN')")
    public AgencySettingsDTO getAgencySettings() {
        return settingsService.toDTO(settingsService.current(), settingsService.currentAgency());
    }

    @PutMapping("/api/settings/agency")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public AgencySettingsDTO updateAgencySettings(@Valid @RequestBody AgencySettingsDTO dto) {
        return settingsService.toDTO(settingsService.update(dto), settingsService.currentAgency());
    }

    /** PRO-025: plan actual y sus limites. */
    @GetMapping("/api/settings/plan")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN')")
    public Map<String, Object> getPlan() {
        Plan plan = settingsService.currentAgency().getPlan();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("plan", plan.name());
        r.put("maxProperties", plan.maxProperties());
        r.put("maxAdmins", plan.maxAdmins());
        return r;
    }

    // --- Plataforma (personal de Nilo) ---

    public record OnboardingRequest(
            @NotBlank(message = "El nombre de la inmobiliaria es obligatorio") String name,
            @NotBlank @Pattern(regexp = "^[a-z0-9-]{3,60}$", message = "El identificador solo admite minusculas, numeros y guiones (3 a 60)") String slug,
            Plan plan,
            @NotBlank(message = "El nombre del administrador es obligatorio") String adminName,
            @NotBlank @Email(message = "El email del administrador no es valido") String adminEmail) {}

    public record AgencyView(UUID id, String name, String slug, String plan, boolean active) {
        static AgencyView of(Agency a) {
            return new AgencyView(a.getId(), a.getName(), a.getSlug(), a.getPlan().name(), a.isActive());
        }
    }

    @PostMapping("/api/platform/agencies")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Map<String, Object>> onboard(@Valid @RequestBody OnboardingRequest req) {
        AgencyOnboardingService.Onboarding o = onboardingService.onboard(
                req.name(), req.slug(), req.plan(), req.adminName(), req.adminEmail());
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("agency", AgencyView.of(o.agency()));
        r.put("superAdminId", o.superAdminId());
        r.put("temporaryPassword", o.temporaryPassword());
        return ResponseEntity.status(HttpStatus.CREATED).body(r);
    }

    @GetMapping("/api/platform/agencies")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public List<AgencyView> listAgencies() {
        return onboardingService.list().stream().map(AgencyView::of).toList();
    }
}

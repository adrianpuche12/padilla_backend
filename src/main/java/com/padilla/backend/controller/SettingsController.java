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

    /** PRO-084: reglas operativas de la agencia (SLA, monto critico, horario de mails, tipos obligatorios). */
    @GetMapping("/api/settings/rules")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN')")
    public Map<String, Object> getRules() {
        return rules(settingsService.current());
    }

    public record RulesRequest(@jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(720) Integer slaHoras,
                               @jakarta.validation.constraints.DecimalMin("0") java.math.BigDecimal montoCritico,
                               @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(23) Integer emailDesdeHora,
                               @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(24) Integer emailHastaHora) {}

    @PutMapping("/api/settings/rules")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Map<String, Object> updateRules(@Valid @RequestBody RulesRequest req) {
        AgencySettingsDTO dto = settingsService.toDTO(settingsService.current(), settingsService.currentAgency());
        if (req.slaHoras() != null) dto.setSlaHoras(req.slaHoras());
        dto.setMontoCritico(req.montoCritico());
        if (req.emailDesdeHora() != null) dto.setEmailDesdeHora(req.emailDesdeHora());
        if (req.emailHastaHora() != null) dto.setEmailHastaHora(req.emailHastaHora());
        return rules(settingsService.update(dto));
    }

    private static Map<String, Object> rules(com.padilla.backend.entity.AgencySettings s) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("slaHoras", s.getSlaHoras());
        r.put("montoCritico", s.getMontoCritico());
        r.put("emailDesdeHora", s.getEmailDesdeHora());
        r.put("emailHastaHora", s.getEmailHastaHora());
        r.put("tiposObligatorios", java.util.Arrays.stream(com.padilla.backend.enums.NotificationType.values())
                .filter(com.padilla.backend.enums.NotificationType::isMandatory).map(Enum::name).toList());
        return r;
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

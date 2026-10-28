package com.padilla.backend.controller;

import com.padilla.backend.exception.RbacException;
import com.padilla.backend.security.CurrentUser;
import com.padilla.backend.service.dashboard.SurveyService;
import com.padilla.backend.service.dashboard.WidgetRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** PRO-070 (Widget Registry) y PRO-071 (encuestas). */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN', 'OWNER', 'TENANT', 'PROVIDER')")
public class DashboardController {

    private final WidgetRegistryService registry;
    private final SurveyService surveys;

    @GetMapping("/api/dashboard/widgets/available")
    public List<Map<String, Object>> available() {
        return registry.available();
    }

    @PutMapping("/api/dashboard/widgets/preferences")
    public List<Map<String, Object>> preferences(@RequestBody List<WidgetRegistryService.Preference> prefs) {
        return registry.savePreferences(prefs);
    }

    @GetMapping("/api/dashboard/data")
    public Map<String, Object> data(@RequestParam Map<String, String> params) {
        return registry.data(params);
    }

    // --- Encuestas ---

    @GetMapping("/api/surveys/pending")
    public List<Map<String, Object>> pendingSurveys() {
        return surveys.pending();
    }

    public record SurveyAnswer(int puntuacion, String comentario) {}

    @PostMapping("/api/surveys/{id}/answer")
    public Map<String, Object> answer(@PathVariable UUID id, @RequestBody SurveyAnswer a) {
        return surveys.answer(id, a.puntuacion(), a.comentario());
    }

    /** Rating de un proveedor: lo ve la inmobiliaria y el propio proveedor. */
    @GetMapping("/api/providers/{id}/rating")
    public Map<String, Object> providerRating(@PathVariable UUID id) {
        if (!CurrentUser.isManagement() && !CurrentUser.id().equals(id)) {
            throw new RbacException("No podes ver la puntuacion de otro proveedor");
        }
        return surveys.providerRating(id);
    }
}

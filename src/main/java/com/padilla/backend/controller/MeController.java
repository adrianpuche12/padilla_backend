package com.padilla.backend.controller;

import com.padilla.backend.dto.auth.LoginRequest;
import com.padilla.backend.entity.User;
import com.padilla.backend.exception.BadRequestException;
import com.padilla.backend.exception.NotFoundException;
import com.padilla.backend.repository.AgencyRepository;
import com.padilla.backend.repository.ClientProfileRepository;
import com.padilla.backend.repository.UserRepository;
import com.padilla.backend.security.AgencyContext;
import com.padilla.backend.security.CurrentUser;
import com.padilla.backend.security.PasswordPolicy;
import com.padilla.backend.service.auth.KeycloakAdminService;
import com.padilla.backend.service.auth.KeycloakAuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PRO-031: "Mi perfil" para los 6 roles (matriz ARQ 7.3: cada uno hace CRUD de su propio perfil).
 */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN', 'OWNER', 'TENANT', 'PROVIDER')")
public class MeController {

    private final UserRepository userRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final AgencyRepository agencyRepository;
    private final KeycloakAuthService keycloakAuthService;
    private final KeycloakAdminService keycloakAdminService;

    public record UpdateMeRequest(
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 255) String name,
            @Size(max = 50) String phone,
            @Size(max = 255) String address,
            @Size(max = 100) String city,
            @Size(max = 100) String province) {}

    public record ChangeMyPasswordRequest(
            @NotBlank(message = "La contraseña actual es obligatoria") String currentPassword,
            @NotBlank(message = "La contraseña nueva es obligatoria") String newPassword) {}

    @GetMapping
    @Transactional(readOnly = true)
    public Map<String, Object> me() {
        User user = currentUser();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", user.getId());
        r.put("name", user.getName());
        r.put("email", user.getEmail());
        r.put("phone", user.getPhone());
        r.put("role", user.getRole());
        r.put("agencyId", AgencyContext.current().orElse(null));
        r.put("agencyName", AgencyContext.current().flatMap(agencyRepository::findById).map(a -> a.getName()).orElse(null));
        clientProfileRepository.findByUserId(user.getId()).ifPresent(p -> {
            r.put("address", p.getAddress());
            r.put("city", p.getCity());
            r.put("province", p.getProvince());
            r.put("documentType", p.getDocumentType());
            r.put("documentNumber", p.getDocumentNumber());
            r.put("specialty", p.getSpecialty());
        });
        return r;
    }

    /** Edita los datos de contacto propios. El email y el rol no se cambian desde aca. */
    @PutMapping
    @Transactional
    public Map<String, Object> update(@Valid @RequestBody UpdateMeRequest req) {
        User user = currentUser();
        user.setName(req.name().trim());
        user.setPhone(req.phone());
        userRepository.save(user);
        clientProfileRepository.findByUserId(user.getId()).ifPresent(p -> {
            p.setAddress(req.address());
            p.setCity(req.city());
            p.setProvince(req.province());
            clientProfileRepository.save(p);
        });
        return me();
    }

    /** Cambio de contrasena propio: exige la actual (se verifica contra Keycloak). */
    @PutMapping("/password")
    public Map<String, String> changePassword(@Valid @RequestBody ChangeMyPasswordRequest req) {
        PasswordPolicy.violation(req.newPassword()).ifPresent(msg -> {
            throw new BadRequestException(msg);
        });
        if (req.newPassword().equals(req.currentPassword())) {
            throw new BadRequestException("La contraseña nueva tiene que ser distinta de la actual");
        }
        User user = currentUser();
        LoginRequest check = new LoginRequest();
        check.setUsername(user.getEmail());
        check.setPassword(req.currentPassword());
        try {
            keycloakAuthService.login(check);
        } catch (HttpClientErrorException e) {
            throw new BadRequestException("La contraseña actual no es correcta");
        }
        keycloakAdminService.setPermanentPasswordById(user.getId().toString(), req.newPassword());
        return Map.of("message", "Contraseña actualizada");
    }

    private User currentUser() {
        UUID id = CurrentUser.id();
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("Usuario no encontrado: " + id));
    }
}

package com.padilla.backend.dto.client;

import com.padilla.backend.enums.PersonaType;
import com.padilla.backend.enums.ProviderSpecialty;
import com.padilla.backend.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientDTO {

    // --- Campos de usuario (tabla users) ---
    private UUID id;
    private String name;
    private String email;
    private String phone;
    private Role role;
    private boolean active;
    private UUID createdBy;
    private OffsetDateTime createdAt;
    private boolean firstLogin;
    private OffsetDateTime passwordResetExpiresAt;

    // --- Campos de perfil cliente (tabla client_profiles) ---
    private UUID profileId;
    private Integer legacyId;
    private PersonaType personaType;
    private ProviderSpecialty specialty;
    private String documentType;
    private String documentNumber;
    private String cuitCuil;
    private String address;
    private String city;
    private String province;
    private String ivaCondition;
    private String notes;

    // Solo presente en la respuesta de creación; null en todas las demás operaciones
    private String temporaryPassword;
}

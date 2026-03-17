package com.padilla.backend.dto.client;

import com.padilla.backend.enums.PersonaType;
import com.padilla.backend.enums.ProviderSpecialty;
import com.padilla.backend.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateClientRequest {

    @NotBlank
    private String name;

    @NotBlank
    @Email
    private String email;

    private String phone;

    @NotNull
    private Role role; // Solo OWNER, TENANT, PROVIDER — validado en el servicio

    @NotNull
    private PersonaType personaType;

    private ProviderSpecialty specialty; // Solo para PROVIDER

    private String documentType;

    private String documentNumber;

    private String cuitCuil;

    private String address;

    private String city;

    private String province;

    private String ivaCondition;

    private String notes;
}

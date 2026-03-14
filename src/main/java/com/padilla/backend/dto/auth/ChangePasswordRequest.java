package com.padilla.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChangePasswordRequest {

    @NotBlank(message = "La nueva contraseña es requerida")
    private String newPassword;
}

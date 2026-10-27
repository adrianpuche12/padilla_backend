package com.padilla.backend.dto.user;

import com.padilla.backend.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {

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
    /** PRO-013: si quien consulta puede gestionar a este usuario. Null fuera de la vista de sistema. */
    private Boolean canManage;
}

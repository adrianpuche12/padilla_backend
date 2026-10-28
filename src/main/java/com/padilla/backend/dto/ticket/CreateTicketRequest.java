package com.padilla.backend.dto.ticket;

import com.padilla.backend.enums.TicketCategory;
import com.padilla.backend.enums.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateTicketRequest {

    @NotBlank(message = "El título es obligatorio")
    private String title;

    @NotBlank(message = "La descripción es obligatoria")
    private String description;

    @NotNull(message = "La categoría es obligatoria")
    private TicketCategory category;

    private TicketPriority priority = TicketPriority.NORMAL;

    @NotNull(message = "La propiedad es obligatoria")
    private UUID propertyId;

    /**
     * PRO-012: opcional. Solo lo usa el personal de la inmobiliaria al abrir un ticket por el inquilino.
     * Si falta, se toma el inquilino del contrato activo de la propiedad.
     */
    private UUID tenantId;

    /** PRO-051: metadata del modo de carga (opcional). */
    private com.padilla.backend.enums.TicketLoadMode modoCarga;

    @jakarta.validation.constraints.PositiveOrZero
    private Integer tiempoCargaSegundos;

    @jakarta.validation.constraints.Pattern(regexp = "^(iOS|Android|Web)$", message = "dispositivo: iOS, Android o Web")
    private String dispositivo;
}

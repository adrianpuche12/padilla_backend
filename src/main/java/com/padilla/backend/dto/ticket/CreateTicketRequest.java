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
}

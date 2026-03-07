package com.padilla.backend.dto.property;

import com.padilla.backend.enums.PropertyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreatePropertyRequest {

    @NotBlank(message = "La dirección es obligatoria")
    private String address;

    @NotNull(message = "El tipo de propiedad es obligatorio")
    private PropertyType type;

    private Double squareMeters;

    private Integer rooms;

    @NotNull(message = "El propietario es obligatorio")
    private UUID ownerId;
}

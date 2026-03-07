package com.padilla.backend.dto.contract;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreateContractRequest {

    @NotNull(message = "La propiedad es obligatoria")
    private UUID propertyId;

    @NotNull(message = "El propietario es obligatorio")
    private UUID ownerId;

    @NotNull(message = "El inquilino es obligatorio")
    private UUID tenantId;

    @NotNull(message = "La fecha de inicio es obligatoria")
    private LocalDate startDate;

    @NotNull(message = "La fecha de fin es obligatoria")
    private LocalDate endDate;

    @NotNull(message = "El monto mensual es obligatorio")
    @Positive(message = "El monto debe ser positivo")
    private BigDecimal monthlyAmount;

    private String currency = "ARS";
}

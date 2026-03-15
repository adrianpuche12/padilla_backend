package com.padilla.backend.dto.contract;

import com.padilla.backend.enums.ContractType;
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

    private LocalDate signingDate;

    @NotNull(message = "El monto mensual es obligatorio")
    @Positive(message = "El monto debe ser positivo")
    private BigDecimal monthlyAmount;

    private String currency = "ARS";

    private ContractType contractType;

    @Positive(message = "El porcentaje de honorarios debe ser positivo")
    private BigDecimal commissionPct;

    @Positive(message = "El porcentaje de gastos administrativos debe ser positivo")
    private BigDecimal adminFeePct;

    private String coOwner;

    private String coTenant;

    private String notes;

    private String producer;
}

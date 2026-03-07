package com.padilla.backend.dto.contract;

import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class UpdateContractRequest {

    @Positive(message = "El monto debe ser positivo")
    private BigDecimal monthlyAmount;

    private String currency;

    private LocalDate endDate;
}

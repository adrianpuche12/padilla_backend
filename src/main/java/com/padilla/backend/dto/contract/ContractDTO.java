package com.padilla.backend.dto.contract;

import com.padilla.backend.enums.ContractStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContractDTO {
    private UUID id;
    private UUID propertyId;
    private UUID ownerId;
    private UUID tenantId;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal monthlyAmount;
    private String currency;
    private ContractStatus status;
    private boolean active;
    private OffsetDateTime createdAt;
}

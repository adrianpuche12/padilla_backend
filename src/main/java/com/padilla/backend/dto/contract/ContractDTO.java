package com.padilla.backend.dto.contract;

import com.padilla.backend.enums.ContractStatus;
import com.padilla.backend.enums.ContractType;
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
    private Integer legacyId;
    private UUID propertyId;
    private UUID ownerId;
    private UUID tenantId;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate signingDate;
    private BigDecimal monthlyAmount;
    private String currency;
    private ContractStatus status;
    private ContractType contractType;
    private BigDecimal commissionPct;
    private BigDecimal adminFeePct;
    private String coOwner;
    private String coTenant;
    private String notes;
    private String producer;
    private boolean active;
    private OffsetDateTime createdAt;
}

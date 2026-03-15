package com.padilla.backend.dto.contract;

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
public class ContractPeriodDTO {
    private UUID id;
    private UUID contractId;
    private LocalDate periodFrom;
    private BigDecimal rentAmount;
    private BigDecimal commissionAmount;
    private BigDecimal adminFeeAmount;
    private BigDecimal adjustmentIndex;
    private OffsetDateTime createdAt;
}

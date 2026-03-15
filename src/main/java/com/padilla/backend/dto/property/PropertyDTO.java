package com.padilla.backend.dto.property;

import com.padilla.backend.enums.PropertyRentalStatus;
import com.padilla.backend.enums.PropertySaleStatus;
import com.padilla.backend.enums.PropertyStatus;
import com.padilla.backend.enums.PropertyType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
public class PropertyDTO {
    private UUID id;
    private String address;
    private PropertyType type;
    private Double squareMeters;
    private Integer rooms;
    private UUID ownerId;
    private UUID tenantId;
    private PropertyStatus status;
    private boolean active;
    private OffsetDateTime createdAt;

    // Campos legacy
    private Integer legacyId;
    private String street;
    private String streetNumber;
    private String floor;
    private String apartment;
    private String city;
    private String province;
    private PropertyRentalStatus rentalStatus;
    private PropertySaleStatus saleStatus;
    private LocalDate entryDate;
    private String rentalProducer;
    private String saleProducer;
}

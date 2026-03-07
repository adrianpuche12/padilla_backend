package com.padilla.backend.dto.property;

import com.padilla.backend.enums.PropertyStatus;
import com.padilla.backend.enums.PropertyType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
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
}

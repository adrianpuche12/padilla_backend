package com.padilla.backend.dto.property;

import com.padilla.backend.enums.PropertyStatus;
import lombok.Data;

@Data
public class UpdatePropertyRequest {
    private String address;
    private Double squareMeters;
    private Integer rooms;
    private PropertyStatus status;
}

package com.padilla.backend.dto.property;

import com.padilla.backend.enums.PropertyRentalStatus;
import com.padilla.backend.enums.PropertySaleStatus;
import com.padilla.backend.enums.PropertyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
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

    // Campos opcionales (legacy + datos Excel)
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

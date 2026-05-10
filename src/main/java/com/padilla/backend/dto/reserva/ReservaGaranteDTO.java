package com.padilla.backend.dto.reserva;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReservaGaranteDTO {
    private UUID id;
    private UUID reservaId;
    private String nombre;
    private String dni;
    private String cuil;
    private String domicilio;
    private String descripcionBien;
}

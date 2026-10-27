package com.padilla.backend.dto.reserva;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReservaDTO {
    private UUID id;
    private String nombre;
    private String apellido;
    private String apellidoMaterno;
    private String dni;
    private String cuil;
    private LocalDate fechaNacimiento;
    private String domicilioActual;
    private String telefono;
    private String email;
    private String condicionFiscal;
    private String profesion;
    private String propiedad;
    private UUID propertyId;
    private BigDecimal monto;
    private BigDecimal montoInformes;
    private String tipoGarantia;
    private UUID operadorId;
    private String status;
    private Integer cantidadPersonas;
    private Boolean tieneCoinquilino;
    private String coinquilinoNombre;
    private String tipoInmueble;
    private String asesor;
    private LocalDate fechaRecibo;
    private String medioPago;
    private String observaciones;
    private String apoderadoLocativa;
    private String locadorNombre;
    private String locadorApellido;
    private String locadorDni;
    private String locadorDomicilio;
    private OffsetDateTime createdAt;
    private List<ReservaGaranteDTO> garantes;
}

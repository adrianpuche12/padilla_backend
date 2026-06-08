package com.padilla.backend.dto.reserva;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class CreateReservaRequest {

    @NotBlank
    private String nombre;

    @NotBlank
    private String apellido;

    private String apellidoMaterno;

    @NotBlank
    private String dni;

    private String cuil;
    private LocalDate fechaNacimiento;
    private String domicilioActual;
    private String telefono;
    private String email;
    private String condicionFiscal;
    private String profesion;

    @NotBlank
    private String propiedad;

    private UUID propertyId;

    @NotNull
    private BigDecimal monto;

    private BigDecimal montoInformes;
    private String tipoGarantia;
    private Integer cantidadPersonas;
    private Boolean tieneCoinquilino;
    private String coinquilinoNombre;

    private String tipoInmueble;
    private String asesor;
    private LocalDate fechaRecibo;
    private String medioPago;
    private String observaciones;
    private String apoderadoLocativa;

    // Locador / propietario (feedback cliente 2026-06-08) — lo carga el operador
    private String locadorNombre;
    private String locadorApellido;
    private String locadorDni;
    private String locadorDomicilio;

    private List<GaranteInput> garantes;

    @Data
    public static class GaranteInput {
        @NotBlank
        private String nombre;
        private String dni;
        private String cuil;
        private String domicilio;
        private String descripcionBien;
        private String apellidoMaterno;
        private LocalDate fechaNacimiento;
    }
}

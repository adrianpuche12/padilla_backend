package com.padilla.backend.dto.settings;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** PRO-024: configuracion de la inmobiliaria (lectura y edicion). */
@Data
public class AgencySettingsDTO {

    private UUID agencyId;
    private String agencyName;
    private String plan;

    @Size(max = 200) private String razonSocial;
    @Size(max = 200) private String nombreComercial;
    @Size(max = 150) private String corredorNombre;
    @Size(max = 100) private String corredorMatricula;
    @Size(max = 150) private String colegio;
    @Size(max = 20) private String cuit;
    @Email(message = "El email de contratos no es valido") @Size(max = 150) private String emailContratos;
    @Size(max = 50) private String telefono;
    @Size(max = 200) private String domicilio;
    @Size(max = 100) private String ciudad;
    @Size(max = 100) private String provincia;
    @Size(max = 200) private String jurisdiccion;
    @DecimalMin("0") @DecimalMax("100") private BigDecimal comisionPct;
    private Boolean comisionIva;
    @Size(max = 100) private String plataformaPago;
    @Size(max = 500) private String logoUrl;
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "El color tiene que tener el formato #RRGGBB") private String colorPrimario;
    @Min(1) @Max(720) private Integer slaHoras;
    @DecimalMin("0") private BigDecimal montoCritico;
    @Min(0) @Max(23) private Integer emailDesdeHora;
    @Min(1) @Max(24) private Integer emailHastaHora;
    /** Solo lectura: lo habilita la plataforma. */
    private Boolean leadsEnabled;
    private OffsetDateTime updatedAt;
}

package com.padilla.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "reservas", schema = "padilla_dev")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String apellido;

    @Column(name = "apellido_materno")
    private String apellidoMaterno;

    @Column(nullable = false, length = 20)
    private String dni;

    @Column(length = 20)
    private String cuil;

    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    @Column(name = "domicilio_actual")
    private String domicilioActual;

    @Column(length = 50)
    private String telefono;

    @Column(length = 150)
    private String email;

    @Column(name = "condicion_fiscal", length = 80)
    private String condicionFiscal;

    @Column(length = 100)
    private String profesion;

    @Column(nullable = false)
    private String propiedad;

    @Column(name = "property_id")
    private UUID propertyId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal monto;

    @Column(name = "monto_informes", precision = 14, scale = 2)
    private BigDecimal montoInformes;

    @Column(name = "tipo_garantia", length = 100)
    private String tipoGarantia;

    @Column(name = "operador_id")
    private UUID operadorId;

    @Column(nullable = false, length = 20)
    private String status = "PENDIENTE";

    @Column(name = "cantidad_personas")
    private Integer cantidadPersonas;

    @Column(name = "tiene_co_inquilino")
    private Boolean tieneCoinquilino = false;

    @Column(name = "co_inquilino_nombre", length = 200)
    private String coinquilinoNombre;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }
}

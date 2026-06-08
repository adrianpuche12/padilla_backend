package com.padilla.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "reserva_garantes", schema = "padilla_dev")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReservaGarante {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "reserva_id", nullable = false)
    private UUID reservaId;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(length = 20)
    private String dni;

    @Column(length = 20)
    private String cuil;

    @Column(length = 255)
    private String domicilio;

    @Column(name = "descripcion_bien", columnDefinition = "TEXT")
    private String descripcionBien;

    @Column(name = "apellido_materno", length = 150)
    private String apellidoMaterno;

    @Column(name = "fecha_nacimiento")
    private java.time.LocalDate fechaNacimiento;
}

package com.padilla.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "daily_leads", schema = "padilla")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyLead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tipo_lead")
    private String tipoLead;

    @Column(name = "nombre")
    private String nombre;

    @Column(name = "telefono")
    private String telefono;

    @Column(name = "link_property")
    private String linkProperty;

    @Column(name = "source")
    private String source;

    @Column(name = "name_seller")
    private String nameSeller;

    @Column(name = "date")
    private LocalDate date;
}

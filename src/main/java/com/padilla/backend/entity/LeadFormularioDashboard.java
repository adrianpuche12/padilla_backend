package com.padilla.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "lead_formulario_dashboard", schema = "padilla")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeadFormularioDashboard {

    @Id
    private Integer id;

    @Column(name = "remotejid_hash")
    private String remotejidHash;

    @Column(name = "remotejid")
    private String remotejid;

    @Column(name = "nombre")
    private String nombre;

    @Column(name = "email")
    private String email;

    @Column(name = "telefono")
    private String telefono;

    @Column(name = "motivo")
    private String motivo;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "link_property")
    private String linkProperty;

    @Column(name = "link_form")
    private String linkForm;

    @Column(name = "send_form")
    private Boolean sendForm;

    @Column(name = "answered_form")
    private Boolean answeredForm;

    @Column(name = "id_source")
    private Integer idSource;

    @Column(name = "source")
    private String source;

    @Column(name = "vendedor")
    private String vendedor;

    @Column(name = "id_seller")
    private Integer idSeller;

    @Column(name = "survey_sent")
    private Boolean surveySent;

    @Column(name = "survey_respond")
    private Boolean surveyRespond;

    @Column(name = "date")
    private LocalDate date;

    @Column(name = "createdat")
    private OffsetDateTime createdAt;

    @Column(name = "updatedat")
    private OffsetDateTime updatedAt;
}

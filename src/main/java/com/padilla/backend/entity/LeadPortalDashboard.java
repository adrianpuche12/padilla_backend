package com.padilla.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "lead_portal_dashboard", schema = "padilla")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeadPortalDashboard {

    @Id
    private Integer id;

    @Column(name = "remotejid_hash")
    private String remotejidHash;

    @Column(name = "remotejid")
    private String remotejid;

    @Column(name = "nombre")
    private String nombre;

    @Column(name = "telefono")
    private String telefono;

    @Column(name = "link_property")
    private String linkProperty;

    @Column(name = "id_source")
    private Integer idSource;

    @Column(name = "source")
    private String source;

    @Column(name = "vendedor")
    private String vendedor;

    @Column(name = "id_seller")
    private Integer idSeller;

    @Column(name = "date")
    private LocalDate date;

    @Column(name = "createdat")
    private OffsetDateTime createdAt;

    @Column(name = "updatedat")
    private OffsetDateTime updatedAt;
}

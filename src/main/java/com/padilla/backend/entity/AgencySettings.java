package com.padilla.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * PRO-024: datos legales, de marca y reglas operativas de una inmobiliaria.
 * Reemplaza lo que estaba escrito en el codigo (documentos FC1/FC10/CTR y mails con datos de Padilla).
 */
@Entity
@Table(name = "agency_settings")
@Data
@NoArgsConstructor
public class AgencySettings {

    @Id
    @Column(name = "agency_id")
    private UUID agencyId;

    @Column(name = "razon_social") private String razonSocial;
    @Column(name = "nombre_comercial") private String nombreComercial;
    @Column(name = "corredor_nombre") private String corredorNombre;
    @Column(name = "corredor_matricula") private String corredorMatricula;
    @Column(name = "colegio") private String colegio;
    @Column(name = "cuit") private String cuit;
    @Column(name = "email_contratos") private String emailContratos;
    @Column(name = "telefono") private String telefono;
    @Column(name = "domicilio") private String domicilio;
    @Column(name = "ciudad") private String ciudad;
    @Column(name = "provincia") private String provincia;
    @Column(name = "jurisdiccion") private String jurisdiccion;
    @Column(name = "comision_pct") private BigDecimal comisionPct;
    @Column(name = "comision_iva", nullable = false) private boolean comisionIva = true;
    @Column(name = "plataforma_pago") private String plataformaPago;
    @Column(name = "logo_url") private String logoUrl;
    @Column(name = "color_primario") private String colorPrimario;
    @Column(name = "sla_horas", nullable = false) private int slaHoras = 48;
    @Column(name = "monto_critico") private BigDecimal montoCritico;
    @Column(name = "email_desde_hora", nullable = false) private short emailDesdeHora = 8;
    @Column(name = "email_hasta_hora", nullable = false) private short emailHastaHora = 20;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;
    @Column(name = "updated_by") private UUID updatedBy;

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = OffsetDateTime.now();
    }
}

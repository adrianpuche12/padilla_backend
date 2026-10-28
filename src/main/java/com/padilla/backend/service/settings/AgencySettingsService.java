package com.padilla.backend.service.settings;

import com.padilla.backend.dto.settings.AgencySettingsDTO;
import com.padilla.backend.entity.Agency;
import com.padilla.backend.entity.AgencySettings;
import com.padilla.backend.enums.Plan;
import com.padilla.backend.enums.Role;
import com.padilla.backend.exception.BadRequestException;
import com.padilla.backend.exception.ConflictException;
import com.padilla.backend.exception.NotFoundException;
import com.padilla.backend.exception.RbacException;
import com.padilla.backend.repository.AgencyRepository;
import com.padilla.backend.repository.AgencySettingsRepository;
import com.padilla.backend.security.AgencyContext;
import com.padilla.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * PRO-024: configuracion de la inmobiliaria del usuario actual.
 * PRO-025: limites del plan.
 */
@Service
@RequiredArgsConstructor
public class AgencySettingsService {

    private final AgencySettingsRepository settingsRepository;
    private final AgencyRepository agencyRepository;
    private final JdbcTemplate jdbc;

    @Value("${app.db.schema}")
    private String schema;

    /** Configuracion de la agencia actual; si todavia no existe se devuelve vacia (no falla). */
    @Transactional(readOnly = true)
    public AgencySettings current() {
        UUID agency = AgencyContext.require();
        return settingsRepository.findById(agency).orElseGet(() -> {
            AgencySettings empty = new AgencySettings();
            empty.setAgencyId(agency);
            return empty;
        });
    }

    @Transactional(readOnly = true)
    public Agency currentAgency() {
        UUID agency = AgencyContext.require();
        return agencyRepository.findById(agency)
                .orElseThrow(() -> new NotFoundException("Inmobiliaria no encontrada: " + agency));
    }

    /** Solo el SUPER_ADMIN de la inmobiliaria edita su configuracion (ARQ 7.1). */
    @Transactional
    public AgencySettings update(AgencySettingsDTO dto) {
        if (CurrentUser.role() != Role.SUPER_ADMIN) {
            throw new RbacException("Solo el SUPER_ADMIN de la inmobiliaria puede editar su configuracion");
        }
        if (dto.getEmailDesdeHora() != null && dto.getEmailHastaHora() != null
                && dto.getEmailDesdeHora() >= dto.getEmailHastaHora()) {
            throw new BadRequestException("El horario de envio de mails no es valido (desde tiene que ser menor que hasta)");
        }
        AgencySettings s = current();
        s.setRazonSocial(dto.getRazonSocial());
        s.setNombreComercial(dto.getNombreComercial());
        s.setCorredorNombre(dto.getCorredorNombre());
        s.setCorredorMatricula(dto.getCorredorMatricula());
        s.setColegio(dto.getColegio());
        s.setCuit(dto.getCuit());
        s.setEmailContratos(dto.getEmailContratos());
        s.setTelefono(dto.getTelefono());
        s.setDomicilio(dto.getDomicilio());
        s.setCiudad(dto.getCiudad());
        s.setProvincia(dto.getProvincia());
        s.setJurisdiccion(dto.getJurisdiccion());
        s.setComisionPct(dto.getComisionPct());
        if (dto.getComisionIva() != null) s.setComisionIva(dto.getComisionIva());
        s.setPlataformaPago(dto.getPlataformaPago());
        s.setLogoUrl(dto.getLogoUrl());
        s.setColorPrimario(dto.getColorPrimario());
        if (dto.getSlaHoras() != null) s.setSlaHoras(dto.getSlaHoras());
        s.setMontoCritico(dto.getMontoCritico());
        if (dto.getEmailDesdeHora() != null) s.setEmailDesdeHora(dto.getEmailDesdeHora().shortValue());
        if (dto.getEmailHastaHora() != null) s.setEmailHastaHora(dto.getEmailHastaHora().shortValue());
        s.setUpdatedBy(CurrentUser.id());
        return settingsRepository.save(s);
    }

    // --- PRO-025: limites del plan ---

    @Transactional(readOnly = true)
    public void checkCanAddProperty() {
        Plan plan = currentAgency().getPlan();
        if (plan.maxProperties() == null) return;
        Integer actuales = jdbc.queryForObject("SELECT count(*) FROM " + schema + ".properties WHERE active", Integer.class);
        if (actuales != null && actuales >= plan.maxProperties()) {
            throw new ConflictException("El plan " + plan + " permite hasta " + plan.maxProperties()
                    + " propiedades activas. Para cargar mas, cambia de plan.");
        }
    }

    @Transactional(readOnly = true)
    public void checkCanAddAdmin() {
        Plan plan = currentAgency().getPlan();
        if (plan.maxAdmins() == null) return;
        Integer actuales = jdbc.queryForObject(
                "SELECT count(*) FROM " + schema + ".users WHERE active AND role = 'ADMIN'", Integer.class);
        if (actuales != null && actuales >= plan.maxAdmins()) {
            throw new ConflictException("El plan " + plan + " permite hasta " + plan.maxAdmins()
                    + " usuarios ADMIN. Para sumar mas, cambia de plan.");
        }
    }

    public AgencySettingsDTO toDTO(AgencySettings s, Agency agency) {
        AgencySettingsDTO d = new AgencySettingsDTO();
        d.setAgencyId(s.getAgencyId());
        d.setAgencyName(agency.getName());
        d.setPlan(agency.getPlan().name());
        d.setRazonSocial(s.getRazonSocial());
        d.setNombreComercial(s.getNombreComercial());
        d.setCorredorNombre(s.getCorredorNombre());
        d.setCorredorMatricula(s.getCorredorMatricula());
        d.setColegio(s.getColegio());
        d.setCuit(s.getCuit());
        d.setEmailContratos(s.getEmailContratos());
        d.setTelefono(s.getTelefono());
        d.setDomicilio(s.getDomicilio());
        d.setCiudad(s.getCiudad());
        d.setProvincia(s.getProvincia());
        d.setJurisdiccion(s.getJurisdiccion());
        d.setComisionPct(s.getComisionPct());
        d.setComisionIva(s.isComisionIva());
        d.setPlataformaPago(s.getPlataformaPago());
        d.setLogoUrl(s.getLogoUrl());
        d.setColorPrimario(s.getColorPrimario());
        d.setSlaHoras(s.getSlaHoras());
        d.setMontoCritico(s.getMontoCritico());
        d.setEmailDesdeHora((int) s.getEmailDesdeHora());
        d.setEmailHastaHora((int) s.getEmailHastaHora());
        d.setUpdatedAt(s.getUpdatedAt());
        return d;
    }
}

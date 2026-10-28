package com.padilla.backend.service.reserva;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.padilla.backend.entity.AgencySettings;
import com.padilla.backend.entity.Contract;
import com.padilla.backend.entity.Reserva;
import com.padilla.backend.entity.ReservaGarante;
import com.padilla.backend.entity.User;
import com.padilla.backend.enums.ContractStatus;
import com.padilla.backend.enums.ReservaStatus;
import com.padilla.backend.enums.Role;
import com.padilla.backend.exception.BadRequestException;
import com.padilla.backend.exception.ConflictException;
import com.padilla.backend.exception.NotFoundException;
import com.padilla.backend.repository.PropertyRepository;
import com.padilla.backend.repository.UserRepository;
import com.padilla.backend.service.contract.ContractService;
import com.padilla.backend.service.settings.AgencySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PRO-082 / PRO-083: documentos de reserva (FC1, FC10, CTR) con los datos de la agencia (ya no los de
 * Padilla escritos en el codigo) y contrato precargado a partir de una reserva APROBADA (decision D8).
 */
@Service
@RequiredArgsConstructor
public class ReservaDocumentService {

    private static final DateTimeFormatter F = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ReservaService reservaService;
    private final AgencySettingsService settingsService;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final ContractService contractService;

    /** Datos para armar los documentos en el frontend: reserva, garantes y agencia. */
    @Transactional(readOnly = true)
    public Map<String, Object> documentData(UUID reservaId) {
        Reserva r = reservaService.findById(reservaId).orElseThrow(() -> new NotFoundException("Reserva no encontrada: " + reservaId));
        AgencySettings a = settingsService.current();
        Map<String, Object> agencia = new LinkedHashMap<>();
        agencia.put("razonSocial", a.getRazonSocial());
        agencia.put("nombreComercial", a.getNombreComercial());
        agencia.put("corredorNombre", a.getCorredorNombre());
        agencia.put("corredorMatricula", a.getCorredorMatricula());
        agencia.put("colegio", a.getColegio());
        agencia.put("emailContratos", a.getEmailContratos());
        agencia.put("telefono", a.getTelefono());
        agencia.put("domicilio", a.getDomicilio());
        agencia.put("ciudad", a.getCiudad());
        agencia.put("provincia", a.getProvincia());
        agencia.put("jurisdiccion", a.getJurisdiccion());
        agencia.put("comisionPct", a.getComisionPct());
        agencia.put("comisionIva", a.isComisionIva());
        agencia.put("plataformaPago", a.getPlataformaPago());
        agencia.put("logoUrl", a.getLogoUrl());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("agencia", agencia);
        out.put("reservaId", r.getId());
        out.put("garantes", reservaService.findGarantes(reservaId).stream().map(ReservaGarante::getNombre).toList());
        return out;
    }

    /** PRO-083: PDF del documento generado en el servidor (resumen con los datos clave). */
    @Transactional(readOnly = true)
    public byte[] pdf(UUID reservaId, String doc) {
        Reserva r = reservaService.findById(reservaId).orElseThrow(() -> new NotFoundException("Reserva no encontrada: " + reservaId));
        AgencySettings a = settingsService.current();
        String titulo = switch (doc.toLowerCase()) {
            case "fc1" -> "FC1 - Recibo de Reserva";
            case "fc10" -> "FC10 - Entrega de copia del Recibo de Reserva";
            case "ctr" -> "CTR - Datos para el Contrato de Locación";
            default -> throw new BadRequestException("Documento invalido: " + doc + " (fc1, fc10 o ctr)");
        };
        List<ReservaGarante> garantes = reservaService.findGarantes(reservaId);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document d = new Document(PageSize.A4, 50, 50, 50, 50);
        PdfWriter.getInstance(d, out);
        d.open();
        Font h1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, new Color(0x0f, 0x45, 0x38));
        Font b = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        Font n = FontFactory.getFont(FontFactory.HELVETICA, 10);
        d.add(new Paragraph(nv(a.getNombreComercial(), "Inmobiliaria"), h1));
        d.add(new Paragraph(nv(a.getRazonSocial(), "") + (a.getCuit() != null ? " — CUIT " + a.getCuit() : ""), n));
        d.add(new Paragraph(nv(a.getDomicilio(), "") + " " + nv(a.getCiudad(), "") + " " + nv(a.getProvincia(), ""), n));
        d.add(new Paragraph(" "));
        d.add(new Paragraph(titulo, h1));
        String fecha = (r.getFechaRecibo() != null ? r.getFechaRecibo() : LocalDate.now()).format(F);
        d.add(new Paragraph(nv(a.getCiudad(), "") + ", " + fecha, n));
        d.add(new Paragraph(" "));
        d.add(linea("Inquilino", r.getNombre() + " " + r.getApellido() + " — DNI " + r.getDni(), b, n));
        d.add(linea("Domicilio actual", nv(r.getDomicilioActual(), "-"), b, n));
        d.add(linea("Inmueble", r.getPropiedad(), b, n));
        d.add(linea("Monto de la reserva", "$ " + r.getMonto(), b, n));
        if (r.getMontoInformes() != null) d.add(linea("Informes", "$ " + r.getMontoInformes(), b, n));
        d.add(linea("Medio de pago", nv(r.getMedioPago(), "-"), b, n));
        d.add(linea("Garantía", nv(r.getTipoGarantia(), "-"), b, n));
        for (ReservaGarante g : garantes) {
            d.add(linea("Garante", g.getNombre() + (g.getDni() != null ? " — DNI " + g.getDni() : ""), b, n));
        }
        if ("ctr".equalsIgnoreCase(doc)) {
            d.add(linea("Locador", nv(r.getLocadorNombre(), "") + " " + nv(r.getLocadorApellido(), "") + " — DNI " + nv(r.getLocadorDni(), "-"), b, n));
            d.add(linea("Jurisdicción", nv(a.getJurisdiccion(), "-"), b, n));
        }
        if (a.getComisionPct() != null) {
            d.add(linea("Honorarios", a.getComisionPct() + " %" + (a.isComisionIva() ? " + IVA" : ""), b, n));
        }
        d.add(new Paragraph(" "));
        d.add(new Paragraph(" "));
        d.add(new Paragraph("______________________________", n));
        d.add(new Paragraph(nv(a.getCorredorNombre(), ""), b));
        d.add(new Paragraph(nv(a.getCorredorMatricula(), ""), n));
        if (a.getEmailContratos() != null) d.add(new Paragraph(a.getEmailContratos(), n));
        d.close();
        return out.toByteArray();
    }

    public record ContractFromReserva(LocalDate startDate, LocalDate endDate, BigDecimal monthlyAmount, UUID tenantId, String currency) {}

    /**
     * Decision D8: una reserva APROBADA genera el contrato precargado. El inquilino es el indicado o,
     * si falta, el usuario TENANT con el email de la reserva. La propiedad es la de la reserva.
     */
    @Transactional
    public Contract createContract(UUID reservaId, ContractFromReserva req) {
        Reserva r = reservaService.findById(reservaId).orElseThrow(() -> new NotFoundException("Reserva no encontrada: " + reservaId));
        if (ReservaStatus.parse(r.getStatus()) != ReservaStatus.APROBADA) {
            throw new ConflictException("Solo una reserva APROBADA puede generar un contrato");
        }
        if (r.getPropertyId() == null) {
            throw new BadRequestException("La reserva no esta vinculada a una propiedad del sistema");
        }
        if (req.startDate() == null || req.endDate() == null || !req.endDate().isAfter(req.startDate())) {
            throw new BadRequestException("Indica fecha de inicio y de fin (fin posterior al inicio)");
        }
        UUID tenantId = req.tenantId();
        if (tenantId == null && r.getEmail() != null) {
            tenantId = userRepository.findByEmail(r.getEmail().toLowerCase().trim())
                    .filter(u -> u.getRole() == Role.TENANT).map(User::getId).orElse(null);
        }
        if (tenantId == null) {
            throw new BadRequestException("Da de alta al inquilino (o indica tenantId) antes de generar el contrato");
        }
        var property = propertyRepository.findById(r.getPropertyId())
                .orElseThrow(() -> new NotFoundException("Propiedad no encontrada: " + r.getPropertyId()));
        Contract c = new Contract();
        c.setPropertyId(property.getId());
        c.setOwnerId(property.getOwnerId());
        c.setTenantId(tenantId);
        c.setStartDate(req.startDate());
        c.setEndDate(req.endDate());
        c.setMonthlyAmount(req.monthlyAmount() != null ? req.monthlyAmount() : r.getMonto());
        c.setCurrency(req.currency() != null ? req.currency() : "ARS");
        c.setStatus(ContractStatus.ACTIVE);
        c.setActive(true);
        c.setNotes("Generado desde la reserva " + r.getId());
        return contractService.create(c);
    }

    private static Paragraph linea(String k, String v, Font b, Font n) {
        Paragraph p = new Paragraph();
        p.add(new com.lowagie.text.Chunk(k + ": ", b));
        p.add(new com.lowagie.text.Chunk(v == null ? "-" : v, n));
        return p;
    }

    private static String nv(String v, String def) {
        return v == null || v.isBlank() ? def : v;
    }
}
